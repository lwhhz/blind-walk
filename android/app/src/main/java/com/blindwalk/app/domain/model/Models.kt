package com.blindwalk.app.domain.model

import kotlinx.serialization.Serializable

/** 四条随机指令。没有距离、没有方位角：走多远由用户自己判断。 */
enum class InstructionType(val label: String, val arrow: String) {
    FORWARD("前进", "↑"),
    LEFT("左转", "↰"),
    RIGHT("右转", "↱"),
    U_TURN("掉头", "⤺")
}

enum class SessionStatus { RUNNING, PAUSED, ENDED }

/**
 * 一次「瞎走」的元数据。这是唯一事实来源，服务负责读写它。
 *
 * 计时口径：activeMillisBase 累计所有已结束活动段的毫秒数；
 * 正在运行的活动段由 segmentStartedAt 起算，不写入 base，直到暂停或结束。
 */
@Serializable
data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val status: SessionStatus = SessionStatus.RUNNING,
    val endedAt: Long? = null,
    val activeMillisBase: Long = 0L,
    val segmentStartedAt: Long? = null,
    val segmentIndex: Int = 0,
    val instructionCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val lastLat: Double? = null,
    val lastLon: Double? = null,
    val lastFixAt: Long? = null,
    /** 自定义标题。最长 [TITLE_MAX_LENGTH] 个字符；为空时界面回落到日期。 */
    val title: String? = null,
    /** 置顶。置顶项单独成区，不参与月份分组。 */
    val pinned: Boolean = false,
    /** 进入回收站的时间。非空即表示该行程在回收站里，不应出现在正常列表。 */
    val deletedAt: Long? = null,
    /** 本段行程的步数。设备无计步器或权限被拒时恒为 0，界面据此隐藏该项。 */
    val steps: Int = 0,
    /** 计步器起始读数。TYPE_STEP_COUNTER 是"开机以来累计值"，需要减掉起点。 */
    val stepBaseline: Float? = null
) {
    /** 不含暂停时间的有效时长。 */
    fun activeMillis(now: Long): Long {
        val base = activeMillisBase
        val segStart = segmentStartedAt
        return if (status == SessionStatus.RUNNING && segStart != null) {
            base + (now - segStart).coerceAtLeast(0L)
        } else {
            base
        }
    }

    fun wallMillis(now: Long): Long = (endedAt ?: now) - startedAt

    fun displayTitleOrNull(): String? = title?.takeIf { it.isNotBlank() }
}

/** 卡片与列表共用同一套上限，避免"列表能存、卡片放不下"。 */
const val TITLE_MAX_LENGTH = 12

@Serializable
data class TrackPoint(
    val t: Long,
    val lat: Double,
    val lon: Double,
    val acc: Float,
    val seg: Int
)

@Serializable
data class LatLon(val lat: Double, val lon: Double)
