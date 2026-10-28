package com.blindwalk.app.location

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * 用加速度计判断"设备是不是真的在动"。
 *
 * 为什么需要它：定位的锚点门限只能挡住几米的抖动，
 * 而室内网络定位动辄 ±30 米误差，相邻两个点就能隔出 20~40 米，
 * 用位移门限去挡，要么挡不住（里程虚增），要么把正常行走也挡掉。
 * 加速度计不关心 GPS 飘不飘，它只回答一个问题：这个设备现在是不是静止的。
 *
 * 判据是重力以外的加速度标准差：走路时手持/口袋里的抖动通常在 1~4 m/s²，
 * 放在桌上时低于 0.1 m/s²。取 0.28 作为分界，留足余量。
 */
class MotionMonitor(private val sensorManager: SensorManager) : SensorEventListener {

    companion object {
        /** 低于这个标准差视为静止（单位 m/s²）。 */
        private const val STILL_STD_THRESHOLD = 0.28f

        /** 取样窗口：SENSOR_DELAY_UI 约 60ms 一次，40 个样本≈2.4 秒。 */
        private const val WINDOW = 40

        /** 样本不足时不下结论，避免刚开始就被误判成静止。 */
        private const val MIN_SAMPLES = 12
    }

    @Volatile
    var isStill: Boolean = false
        private set

    @Volatile
    var hasReading: Boolean = false
        private set

    private val window = ArrayDeque<Float>(WINDOW)
    private var registered = false

    fun start(): Boolean {
        if (registered) return true
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        registered = runCatching {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }.getOrDefault(false)
        return registered
    }

    fun stop() {
        if (!registered) return
        runCatching { sensorManager.unregisterListener(this) }
        registered = false
        window.clear()
        hasReading = false
        isStill = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (v.size < 3) return
        val magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

        if (window.size >= WINDOW) window.removeFirst()
        window.addLast(magnitude)
        if (window.size < MIN_SAMPLES) return

        var mean = 0f
        for (m in window) mean += m
        mean /= window.size

        var variance = 0f
        for (m in window) {
            val d = m - mean
            variance += d * d
        }
        variance /= window.size

        isStill = sqrt(variance) < STILL_STD_THRESHOLD
        hasReading = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
