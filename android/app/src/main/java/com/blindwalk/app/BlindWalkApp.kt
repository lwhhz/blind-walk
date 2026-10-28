package com.blindwalk.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Immutable
import com.blindwalk.app.data.SessionRepository
import com.blindwalk.app.domain.random.InstructionGenerator
import com.blindwalk.app.domain.model.InstructionType
import com.blindwalk.app.location.TripService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/** 手写依赖容器。工程小，不引 Hilt，省掉 kapt/ksp 与构建时间。 */
class AppContainer(context: Context) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    val repository: SessionRepository = SessionRepository(File(context.filesDir, "sessions"), json)

    val instructionGenerator: InstructionGenerator = InstructionGenerator()

    /** 壁纸与主题设置。和会话数据分开存：一个是偏好，一个是"用户资产"。 */
    val wallpaper: com.blindwalk.app.data.WallpaperStore = com.blindwalk.app.data.WallpaperStore(context)

    /**
     * 与组件生命周期无关的协程作用域。
     * 作废的「5 秒后真删」必须挂在进程上，而不是服务或界面——
     * 服务会 stopSelf、界面会被销毁，挂在它们身上就会漏删。
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/**
 * 进程内唯一的状态总线：前台服务写，UI 读。
 * 服务是唯一事实来源，UI 只能发 Intent 请求动作。
 */
object TripStore {

    data class InstructionEvent(val type: InstructionType, val at: Long)

    /**
     * 定位状态，供行程页显示"到底有没有信号"。
     * lastFixAt / accuracyMeters 记录的是**收到的原始定位**（不论是否被采纳），
     * acceptedPoints 才是真正写进轨迹的点数。
     */
    @Immutable
    data class LocationState(
        val locating: Boolean = false,
        val lastFixAt: Long? = null,
        val accuracyMeters: Float? = null,
        val acceptedPoints: Int = 0
    )

    /** 从首页「进行中」卡片点「结束」：切到行程页后自动弹确认框 */
    val pendingEndDialog = MutableStateFlow(false)

    val record = MutableStateFlow<com.blindwalk.app.domain.model.SessionRecord?>(null)
    val instruction = MutableStateFlow<InstructionEvent?>(null)
    val location = MutableStateFlow(LocationState())

    /**
     * 刚被作废、还在 5 秒撤销窗口内的行程 id。
     * 行程页看到它就去首页，首页看到它弹「已作废 · 撤销」。
     */
    val pendingDiscard = MutableStateFlow<String?>(null)

    fun clear() {
        record.value = null
        instruction.value = null
        location.value = LocationState()
    }
}

class BlindWalkApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 启动路径上只做非做不可的事。扫盘清理孤儿目录扔到后台，
        // 它只是在收拾上次崩溃的残留，晚几百毫秒无所谓。
        container.appScope.launch {
            runCatching { container.repository.sweepDiscarded(olderThanMillis = 10_000L) }
        }

        // 进程刚被创建 = 我们的服务此刻不可能在运行，
        // 所以同 ID 的通知必然是上一轮（崩溃 / 老版本）残留的，直接清掉。
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(TripService.NOTIFICATION_ID)
        }
    }
}
