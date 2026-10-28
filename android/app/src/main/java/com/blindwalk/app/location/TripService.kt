package com.blindwalk.app.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.blindwalk.app.AppContainer
import com.blindwalk.app.BlindWalkApp
import com.blindwalk.app.MainActivity
import com.blindwalk.app.R
import com.blindwalk.app.TripStore
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.domain.model.TrackPoint
import com.blindwalk.app.domain.session.SessionReducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 行程前台服务：定位、计时、随机指令、落盘，全部在这里。
 *
 * 高版本 Android 关键点：
 *  - manifest 声明 foregroundServiceType="location" + FOREGROUND_SERVICE_LOCATION 权限；
 *  - 必须在 App 可见时启动（UI 里点按钮触发）；
 *  - START_STICKY + 每次状态变化落盘，进程被杀后能自愈。
 */
class TripService : Service(), LocationListener {

    companion object {
        const val ACTION_START = "com.blindwalk.app.action.START"
        const val ACTION_RECOVER = "com.blindwalk.app.action.RECOVER"
        const val ACTION_DRAW = "com.blindwalk.app.action.DRAW"
        const val ACTION_TOGGLE_PAUSE = "com.blindwalk.app.action.TOGGLE_PAUSE"
        const val ACTION_STOP = "com.blindwalk.app.action.STOP"
        const val ACTION_DISCARD = "com.blindwalk.app.action.DISCARD"

        private const val CHANNEL_ID = "trip"
        const val NOTIFICATION_ID = 1001

        private const val SAMPLE_INTERVAL_MS = 1_000L
        private const val SAMPLE_DISTANCE_M = 2f

        /** 静止（超过 30 秒没有采纳点）后放宽到的采样间隔。 */
        private const val SAMPLE_IDLE_INTERVAL_MS = 5_000L
        private const val STATIONARY_AFTER_MS = 30_000L

        private const val META_FLUSH_MS = 3_000L

        /** 等首个定位的上限。超过就强行开跑，避免在本里被无限卡住。 */
        private const val LOCATE_TIMEOUT_MS = 30_000L

        /**
         * GPS 优先窗口：最近一次 GPS fix 距今小于这个值时，丢弃网络定位点。
         * 网络点常年 ±30m，精度门槛刚好拦住一半、放进来一半，
         * 户外 GPS 可用时让它们混进轨迹只会把线搅乱。
         */
        private const val GPS_PRIORITY_WINDOW_MS = 10_000L

        /** 作废的撤销窗口。到点还没被撤销就真删。 */
        private const val DISCARD_UNDO_WINDOW_MS = 6_000L

        /**
         * 冷启动恢复时，若距最后一次定位证据超过这个间隔，认为 App 长时间没在运行，
         * 不把这段空档计入有效时长（详见 SessionReducer.recoverAfterCrash）。
         */
        private const val RECOVERY_GAP_MS = 120_000L

        fun start(context: Context, recover: Boolean = false) {
            val intent = Intent(context, TripService::class.java)
                .setAction(if (recover) ACTION_RECOVER else ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            val intent = Intent(context, TripService::class.java).setAction(action)
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 落盘专用单线程队列。
     *
     * 定位回调跑在主线程（Looper.getMainLooper()），原来每秒在这里同步写一次
     * points.jsonl、每 3 秒原子写一次 meta.json——这是行程中最明显的卡顿源。
     * 单线程保证追加顺序，同时把 IO 挪出主线程。
     * 挂在 application 的 scope 上，服务 stopSelf 也不会丢掉排队的写。
     */
    private val writeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "blindwalk-writer").apply { isDaemon = true }
    }
    private val writeDispatcher = writeExecutor.asCoroutineDispatcher()

    private lateinit var container: AppContainer
    private lateinit var locationManager: LocationManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var vibrator: Vibrator

    private var record: SessionRecord? = null
    private var tracking = false
    private var ticker: Job? = null
    private var stationaryJob: Job? = null
    private var lastFlushAt = 0L

    private lateinit var sensorManager: SensorManager
    private var stepListener: SensorEventListener? = null
    private lateinit var motion: MotionMonitor

    /** 正在等第一个定位点：此时会话已建，但活动段还没开始计时。 */
    private var locating = false
    private var locateTimeout: Job? = null
    private var acceptedPoints = 0

    /** 最近一次 GPS（非网络）定位的时间，用于 GPS 优先窗口。 */
    private var lastGpsFixAt = 0L

    /** 当前采样间隔：静止时放宽，一移动就收回 1 秒。 */
    private var currentSampleMillis = 0L

    /** 最近一次采纳点的时间，用于判断"是不是停着不动"。 */
    private var lastAcceptedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as BlindWalkApp).container
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motion = MotionMonitor(sensorManager)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        createChannel()
        // 服务实例刚创建：上一次进程留下的同 ID 通知此刻必然是残留（不是本实例发的），
        // 先清掉。放在 onCreate 而不是 onStartCommand，避免每次抽签都 cancel 重发造成闪烁。
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        // 只有"服务还要继续跑"的动作才刷新通知。
        // 结束与作废会 stopSelf，此时若再 notify 一次，通知就会脱离前台服务永久驻留。
        val keepRunning = when (intent?.action) {
            ACTION_START -> {
                beginNew()
                record != null
            }
            ACTION_DRAW -> {
                draw()
                true
            }
            ACTION_TOGGLE_PAUSE -> {
                togglePause()
                true
            }
            ACTION_STOP -> {
                endSession()
                false
            }
            ACTION_DISCARD -> {
                discardSession()
                false
            }
            // 系统重建（START_STICKY）与显式恢复走同一条路。
            else -> {
                recover()
                record != null
            }
        }

        if (keepRunning) refreshNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
        ticker?.cancel()
        stationaryJob?.cancel()
        scope.cancel()
        // shutdown() 会把手上的任务跑完；shutdownNow() 会丢数据。
        writeExecutor.shutdown()
        // 兜底：服务销毁时确保通知不残留。
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 会话

    private fun beginNew() {
        if (record != null) return
        val now = System.currentTimeMillis()
        // 会话先建（startedAt 记为按下的时刻，卡片时间区间才对），
        // 但活动段留空：等第一个定位点再开始计时。
        // 否则会出现"已经走了 20 秒、总路程还是 0 m"的诡异数据。
        val fresh = SessionReducer.start(UUID.randomUUID().toString(), now)
            .copy(segmentStartedAt = null)
        adopt(fresh)
        saveMeta(fresh)

        locating = true
        acceptedPoints = 0
        publishLocation()
        startTracking()
        startLocateTimeout()
    }

    /** 首个定位点到达（或超时）后，正式起跑。 */
    private fun finishLocating(now: Long) {
        if (!locating) return
        locating = false
        locateTimeout?.cancel()
        locateTimeout = null

        val current = record
        if (current != null && current.segmentStartedAt == null) {
            val started = current.copy(segmentStartedAt = now)
            record = started
            saveMeta(started)
            TripStore.record.value = started
        }
        publishLocation()
        refreshNotification()
    }

    private fun startLocateTimeout() {
        locateTimeout?.cancel()
        locateTimeout = scope.launch {
            delay(LOCATE_TIMEOUT_MS)
            // 一直等不到定位（室内/地下）：强行开跑，界面上会提示"未获得定位"。
            finishLocating(System.currentTimeMillis())
        }
    }

    private fun publishLocation() {
        TripStore.location.value = TripStore.location.value.copy(
            locating = locating,
            acceptedPoints = acceptedPoints
        )
    }

    private fun recover() {
        if (record != null) return
        val unfinished = container.repository.findUnfinished()
        if (unfinished == null) {
            tapHaptic()
            stopSelf()
            return
        }
        // 先用磁盘上的记录把界面填上，重算路程和点数放到后台：
        // 一小时的行程有几千个点，在主线程解析会让服务启动明显卡顿。
        adopt(unfinished)
        locating = false
        publishLocation()
        if (unfinished.status == SessionStatus.RUNNING) startTracking()

        container.appScope.launch(writeDispatcher) {
            val fixed = container.repository.recomputeDistance(unfinished.id) ?: unfinished
            val healed = SessionReducer.recoverAfterCrash(fixed, System.currentTimeMillis(), RECOVERY_GAP_MS)
            if (healed != fixed) container.repository.save(healed)
            val pointCount = container.repository.loadPoints(healed.id).size
            withContext(Dispatchers.Main.immediate) {
                if (record?.id != healed.id) return@withContext
                record = healed
                TripStore.record.value = healed
                acceptedPoints = pointCount
                publishLocation()
                refreshNotification()
            }
        }
    }

    private fun adopt(value: SessionRecord) {
        record = value
        TripStore.record.value = value
        if (value.status == SessionStatus.RUNNING) startTicker()
    }

    private fun draw() {
        val current = record ?: container.repository.findUnfinished()?.also { adopt(it) } ?: return
        if (current.status != SessionStatus.RUNNING) return

        val counted = SessionReducer.countInstruction(current)
        adopt(counted)
        saveMeta(counted)

        val type = container.instructionGenerator.next()
        TripStore.instruction.value = TripStore.InstructionEvent(type, System.currentTimeMillis())
        vibrateOnce()
        refreshNotification()
    }

    private fun togglePause() {
        val current = record ?: return
        val now = System.currentTimeMillis()
        // 暂停时放弃"等定位"，避免 30 秒超时在暂停后突然把计时打开。
        if (locating) {
            locating = false
            locateTimeout?.cancel()
            locateTimeout = null
            publishLocation()
        }
        val next = when (current.status) {
            SessionStatus.RUNNING -> {
                stopTracking()
                SessionReducer.pause(current, now)
            }
            SessionStatus.PAUSED -> {
                startTracking()
                SessionReducer.resume(current, now)
            }
            SessionStatus.ENDED -> return
        }
        adopt(next)
        saveMeta(next)
        if (next.status == SessionStatus.RUNNING) {
            startTicker()
        } else {
            // 暂停时停掉通知刷新的 ticker，没有东西需要每 5 秒更新。
            ticker?.cancel()
            ticker = null
        }
        refreshNotification()
    }

    private fun endSession() {
        val current = record ?: return
        val next = SessionReducer.stop(current, System.currentTimeMillis())
        stopTracking()
        ticker?.cancel()
        ticker = null
        locateTimeout?.cancel()
        locateTimeout = null
        locating = false
        record = next
        // 先把终态推给 UI，再撤前台，避免 UI 收不到结束事件。
        TripStore.record.value = next
        TripStore.location.value = TripStore.LocationState()
        saveMeta(next)
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 显式再取消一次：stopForeground 之后若有任何路径再次 notify，这里兜底。
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun saveMeta(value: SessionRecord) {
        container.repository.save(value)
        lastFlushAt = System.currentTimeMillis()
    }

    /** 轨迹点追加：排进写队列，不阻塞定位回调。 */
    private fun persistPoint(sessionId: String, point: TrackPoint) {
        container.appScope.launch(writeDispatcher) {
            runCatching { container.repository.appendPoint(sessionId, point) }
        }
    }

    /** 周期性 meta 落盘：同样排队列。状态迁移仍走同步 saveMeta 保证顺序。 */
    private fun persistMetaAsync(value: SessionRecord) {
        lastFlushAt = System.currentTimeMillis()
        container.appScope.launch(writeDispatcher) {
            runCatching { container.repository.save(value) }
        }
    }

    /**
     * 作废：不留记录、不生成卡片。
     * 但先搬到暂存区而不是直接删——首页还有 5 秒的「撤销」窗口。
     */
    private fun discardSession() {
        val current = record
        stopTracking()
        ticker?.cancel()
        ticker = null
        locateTimeout?.cancel()
        locateTimeout = null
        locating = false
        record = null

        if (current != null) {
            container.repository.discardToPending(current.id)
            TripStore.pendingDiscard.value = current.id
            scheduleDiscardPurge(current.id)
        }
        TripStore.record.value = null
        TripStore.instruction.value = null
        TripStore.location.value = TripStore.LocationState()

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    /**
     * 撤销窗口结束后真删。挂在整个进程的作用域上：
     * 这个服务马上要 stopSelf，界面也可能被销毁，只有 appScope 能保证执行到底。
     * 用户点了「撤销」会把 pendingDiscard 清空，这里就自然跳过。
     */
    private fun scheduleDiscardPurge(id: String) {
        container.appScope.launch {
            delay(DISCARD_UNDO_WINDOW_MS)
            if (TripStore.pendingDiscard.value == id) {
                container.repository.purgeDiscarded(id)
                TripStore.pendingDiscard.value = null
            }
        }
    }

    // ---------------------------------------------------------------- 定位

    private fun startTracking() {
        if (tracking) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        // GPS 与网络定位同时注册：GPS 精度高但室内无 fix，
        // 网络定位粗糙但能在地下车库/楼宇间给出兜底点。
        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) return

        currentSampleMillis = SAMPLE_INTERVAL_MS
        try {
            providers.forEach { provider -> requestFor(provider, SAMPLE_INTERVAL_MS) }
            tracking = true
        } catch (_: SecurityException) {
            tracking = false
        } catch (_: IllegalArgumentException) {
            tracking = false
        }
        startStationaryWatch()
        startStepCounting()
        motion.start()
    }

    /**
     * 计步。
     *
     * TYPE_STEP_COUNTER 给的是「开机以来累计步数」，所以记住起点做差。
     * 没有传感器、或用户拒了「身体活动」权限时静默跳过：
     * record.stepBaseline 保持 null，界面据此整项隐藏，而不是显示一个假的 0。
     */
    private fun startStepCounting() {
        if (stepListener != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val current = record ?: return
                if (current.status != SessionStatus.RUNNING) return
                val value = event.values.firstOrNull() ?: return
                val baseline = current.stepBaseline
                val updated = if (baseline == null || value < baseline) {
                    // 首次接入，或期间设备重启导致计数归零：以当前读数重新起算。
                    current.copy(stepBaseline = value, steps = 0)
                } else {
                    current.copy(steps = (value - baseline).toInt().coerceAtLeast(0))
                }
                if (updated != current) {
                    record = updated
                    TripStore.record.value = updated
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            stepListener = listener
        }
    }

    private fun stopStepCounting() {
        stepListener?.let { runCatching { sensorManager.unregisterListener(it) } }
        stepListener = null
    }

    private fun requestFor(provider: String, intervalMs: Long) {
        locationManager.requestLocationUpdates(
            provider,
            intervalMs,
            SAMPLE_DISTANCE_M,
            this,
            Looper.getMainLooper()
        )
    }

    /**
     * 静止时把采样从 1 秒放宽到 5 秒。
     *
     * 这样做的意义不在省 CPU —— 在省电。定位耗电主要来自 GPS 芯片被反复唤醒，
     * 站着不动还每秒定位一次纯属浪费。一旦有新点被采纳，立刻收回 1 秒。
     */
    private fun startStationaryWatch() {
        stationaryJob?.cancel()
        stationaryJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (!tracking) continue
                val idle = System.currentTimeMillis() - lastAcceptedAt
                val target = if (idle > STATIONARY_AFTER_MS) SAMPLE_IDLE_INTERVAL_MS else SAMPLE_INTERVAL_MS
                if (target != currentSampleMillis) switchSampling(target)
            }
        }
    }

    private fun switchSampling(intervalMs: Long) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        currentSampleMillis = intervalMs
        runCatching {
            locationManager.removeUpdates(this)
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
                .forEach { requestFor(it, intervalMs) }
        }.onFailure { tracking = false }
    }

    private fun stopTracking() {
        if (!tracking) return
        stationaryJob?.cancel()
        stationaryJob = null
        stopStepCounting()
        motion.stop()
        try {
            locationManager.removeUpdates(this)
        } catch (_: Exception) {
            // 忽略：权限被回收时 removeUpdates 也可能抛。
        }
        tracking = false
    }

    // ------------------------------------------------------------ 通知与反馈

    override fun onLocationChanged(location: Location) {
        val current = record ?: return
        if (current.status != SessionStatus.RUNNING) return

        val now = System.currentTimeMillis()

        // 不管这个点会不会被采纳，都记下"最近一次收到定位"，让界面能显示信号强弱。
        TripStore.location.value = TripStore.location.value.copy(
            lastFixAt = now,
            accuracyMeters = location.accuracy
        )

        val isGps = location.provider == LocationManager.GPS_PROVIDER
        if (isGps) lastGpsFixAt = now

        // 等首点阶段只认"合格的 GPS 点"：网络点常年 ±30m；而 Android 在注册后
        // 可能立刻回调一个**缓存**的 GPS 点（精度可能上百米），拿它当起点同样会毁掉轨迹。
        if (locating) {
            val acceptable = isGps && location.accuracy <= SessionReducer.MAX_ACCURACY_METERS
            if (!acceptable) return
            finishLocating(now)
        }

        // 设备真的静止：GPS 还在漂，但人没动。
        // 这时只把锚点挪到当前读数（跟着漂移中心走），既不记点也不累计里程；
        // 一旦恢复走动，锚点就在脚下，里程从那时开始算。
        //
        // 兜底：GPS 自己报的速度 ≥1 m/s 时一律按"在动"处理。
        // 骑车时手机架在支架上、路面又平，加速度计的标准差可能低于阈值，
        // 只看传感器会把骑行里程整段冻掉。
        val gpsMoving = location.hasSpeed() && location.speed >= 1.0f
        if (!gpsMoving && motion.hasReading && motion.isStill) {
            val still = record ?: return
            val anchored = still.copy(
                lastLat = location.latitude,
                lastLon = location.longitude,
                lastFixAt = now
            )
            record = anchored
            TripStore.record.value = anchored
            return
        }

        // GPS 可用时忽略网络点：网络定位常年 ±30m，会明显搅乱轨迹。
        if (!isGps && now - lastGpsFixAt < GPS_PRIORITY_WINDOW_MS) return

        val latest = record ?: return
        val result = SessionReducer.onLocation(
            latest,
            now,
            location.latitude,
            location.longitude,
            location.accuracy
        )

        val point = result.point
        if (point != null) {
            record = result.record
            persistPoint(latest.id, point)
            acceptedPoints += 1
            lastAcceptedAt = now
            // 一动起来就立刻收回 1 秒采样，别让静止时的降频拖慢轨迹。
            if (currentSampleMillis != SAMPLE_INTERVAL_MS) switchSampling(SAMPLE_INTERVAL_MS)
            TripStore.record.value = result.record
            publishLocation()
            if (now - lastFlushAt >= META_FLUSH_MS) persistMetaAsync(result.record)
        } else if (result.record !== latest) {
            record = result.record
        }
    }

    @Deprecated("Required by LocationListener on older platforms")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    // ------------------------------------------------------------ 通知与反馈

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "行程记录",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "瞎走行程进行中的常驻通知"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val current = record
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            locating -> "正在定位…"
            current?.status == SessionStatus.RUNNING -> "正在瞎走"
            current?.status == SessionStatus.PAUSED -> "已暂停"
            else -> "瞎走"
        }

        val body = when {
            locating -> "等待 GPS 信号，30 秒后自动开始"
            current == null -> "准备记录轨迹"
            else -> {
                val dur = Fmt.durationFull(current.activeMillis(now))
                val dist = Fmt.distance(current.distanceMeters)
                "$dur · $dist · ${current.instructionCount} 次指路"
            }
        }

        val toggleLabel = if (current?.status == SessionStatus.PAUSED) "继续" else "暂停"
        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TripService::class.java).setAction(ACTION_TOGGLE_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, TripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_stat_walk)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent)
            .addAction(0, toggleLabel, toggleIntent)
            .addAction(0, "结束", stopIntent)
            .build()
    }

    private fun refreshNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(5_000)
                refreshNotification()
            }
        }
    }

    private fun vibrateOnce() {
        runCatching {
            // 两段节奏，像盖章的"咯噔"两下：12ms → 40ms 间隔 → 28ms。
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 28), -1))
        }
    }

    /** 无会话时震动一下表示"按了但没开始"。 */
    private fun tapHaptic() {
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
