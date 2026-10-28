package com.blindwalk.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

object Fmt {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** 紧凑时长：有小时用 H:MM，否则 MM:SS。卡片与列表用。 */
    fun duration(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L)) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d", h, m)
        else String.format(java.util.Locale.US, "%02d:%02d", m, s)
    }

    /** 精确时长 H:MM:SS，行程页用。 */
    fun durationFull(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0L)) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    }

    /** 数字与单位分开返回，方便卡片排版。 */
    fun distanceParts(meters: Double): Pair<String, String> {
        return if (meters >= 1000.0) {
            String.format(java.util.Locale.US, "%.2f", meters / 1000.0) to "km"
        } else {
            meters.roundToInt().toString() to "m"
        }
    }

    fun distance(meters: Double): String {
        val (value, unit) = distanceParts(meters)
        return "$value $unit"
    }

    /** 步数：带千分位。5 位数字不加分隔符时三列并排会很难读。 */
    fun steps(count: Int): String =
        String.format(java.util.Locale.US, "%,d", count.coerceAtLeast(0))

    fun date(ts: Long): String {
        val d = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
        return String.format(java.util.Locale.US, "%04d.%02d.%02d", d.year, d.monthValue, d.dayOfMonth)
    }

    fun weekday(ts: Long): String {
        val d = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
        return when (d.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }

    fun time(ts: Long): String {
        val d = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
        return String.format(java.util.Locale.US, "%02d:%02d", d.hour, d.minute)
    }

    fun fileStamp(ts: Long): String {
        val d = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), zone)
        return String.format(
            java.util.Locale.US,
            "%04d%02d%02d_%02d%02d%02d",
            d.year, d.monthValue, d.dayOfMonth, d.hour, d.minute, d.second
        )
    }
}
