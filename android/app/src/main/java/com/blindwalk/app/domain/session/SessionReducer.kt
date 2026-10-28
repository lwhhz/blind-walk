package com.blindwalk.app.domain.session

import com.blindwalk.app.domain.geo.Geo
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.domain.model.TrackPoint
import kotlin.math.max

/**
 * 会话状态机。所有迁移都是纯函数：非法迁移原样返回，不抛异常，
 * 这样服务层可以无脑调用而不会被用户乱点搞崩。
 *
 * 合法迁移：
 *   RUNNING --pause--> PAUSED --resume--> RUNNING
 *   RUNNING/PAUSED --stop--> ENDED（终态，不可再迁移）
 */
object SessionReducer {

    /** 小于这个位移视为 GPS 抖动，不计入路程。重算历史轨迹时也用它。 */
    const val MIN_STEP_METERS: Double = 1.5

    /**
     * 锚点门限的绝对下限（米）。
     * 精度好的时候也要走出这么远才认，否则城市里 5 米精度下站着就能刷出里程。
     */
    const val ANCHOR_MIN_METERS: Double = 5.0

    /**
     * 锚点门限随精度放大的系数：门限 = max(ANCHOR_MIN, 精度 × 该系数)。
     * 0.6 意味着精度 ±10 米时要走出 6 米才认，抖动基本被吃掉。
     */
    const val ANCHOR_ACCURACY_FACTOR: Double = 0.6

    /** 精度差于这个值（米）的定位点直接丢弃。 */
    const val MAX_ACCURACY_METERS: Float = 30f

    /** 超过这个速度（米/秒）视为瞬移，不计里程。 */
    const val MAX_SPEED_MPS: Double = 15.0

    data class PointResult(
        val record: SessionRecord,
        val point: TrackPoint?
    )

    fun start(id: String, now: Long): SessionRecord = SessionRecord(
        id = id,
        startedAt = now,
        status = SessionStatus.RUNNING,
        segmentStartedAt = now
    )

    fun countInstruction(record: SessionRecord): SessionRecord {
        if (record.status == SessionStatus.ENDED) return record
        return record.copy(instructionCount = record.instructionCount + 1)
    }

    fun pause(record: SessionRecord, now: Long): SessionRecord {
        if (record.status != SessionStatus.RUNNING) return record
        val segStart = record.segmentStartedAt ?: now
        val base = record.activeMillisBase + (now - segStart).coerceAtLeast(0L)
        return record.copy(
            status = SessionStatus.PAUSED,
            activeMillisBase = base,
            segmentStartedAt = null,
            // 暂停即开新段：恢复后的轨迹不与暂停前连线。
            segmentIndex = record.segmentIndex + 1
        )
    }

    fun resume(record: SessionRecord, now: Long): SessionRecord {
        if (record.status != SessionStatus.PAUSED) return record
        return record.copy(status = SessionStatus.RUNNING, segmentStartedAt = now)
    }

    fun stop(record: SessionRecord, now: Long): SessionRecord {
        if (record.status == SessionStatus.ENDED) return record
        val base = if (record.status == SessionStatus.RUNNING) {
            record.activeMillisBase + (now - (record.segmentStartedAt ?: now)).coerceAtLeast(0L)
        } else {
            record.activeMillisBase
        }
        return record.copy(
            status = SessionStatus.ENDED,
            activeMillisBase = base,
            segmentStartedAt = null,
            endedAt = now
        )
    }

    fun onLocation(
        record: SessionRecord,
        now: Long,
        lat: Double,
        lon: Double,
        accuracy: Float
    ): PointResult {
        if (record.status != SessionStatus.RUNNING) return PointResult(record, null)
        if (!accuracy.isFinite() || accuracy > MAX_ACCURACY_METERS) return PointResult(record, null)

        val anchorLat = record.lastLat
        val anchorLon = record.lastLon
        if (anchorLat == null || anchorLon == null) {
            // 首个点：直接作为锚点。
            return PointResult(
                record.copy(lastLat = lat, lastLon = lon, lastFixAt = now),
                TrackPoint(t = now, lat = lat, lon = lon, acc = accuracy, seg = record.segmentIndex)
            )
        }

        val delta = Geo.haversineMeters(anchorLat, anchorLon, lat, lon)

        // 锚点门限：必须离开锚点超过 max(最小位移, 精度 × 系数) 才算"真的动了"。
        // 站着不动时 GPS 每秒抖动 2~10 米，原来只挡 1.5 米，于是里程一路虚增。
        val gate = max(ANCHOR_MIN_METERS, accuracy * ANCHOR_ACCURACY_FACTOR)
        if (delta < gate) return PointResult(record, null)

        val dtSeconds = ((now - (record.lastFixAt ?: now)).coerceAtLeast(1L)) / 1000.0
        if (delta / dtSeconds > MAX_SPEED_MPS) {
            // 跳点：不计里程，但把锚点挪过去。
            // 否则后面所有点都会撞上同一条判据，轨迹从此断掉再也接不回来。
            return PointResult(
                record.copy(lastLat = lat, lastLon = lon, lastFixAt = now),
                null
            )
        }

        return PointResult(
            record.copy(
                distanceMeters = record.distanceMeters + delta,
                lastLat = lat,
                lastLon = lon,
                lastFixAt = now
            ),
            TrackPoint(t = now, lat = lat, lon = lon, acc = accuracy, seg = record.segmentIndex)
        )
    }

    /**
     * 进程死亡后的恢复。
     *
     * 判据取「最后一次有证据的时刻」：有定位点用 lastFixAt，否则用段起点。
     * 若距现在超过 maxGapMillis，说明 App 有相当长时间没在运行——
     * 这段时间**不计入有效时长**（用户会觉得凭空多走路），并且开新段让轨迹断开。
     * 短断档（系统快速重启）则保持连续，避免把几秒的空隙切成一堆碎片。
     */
    fun recoverAfterCrash(record: SessionRecord, now: Long, maxGapMillis: Long): SessionRecord {
        if (record.status != SessionStatus.RUNNING) return record

        val segStart = record.segmentStartedAt
        if (segStart == null) {
            // 异常中间态：补一个从当前时刻开始的段。
            return record.copy(segmentStartedAt = now, segmentIndex = record.segmentIndex + 1)
        }

        val evidence = record.lastFixAt ?: segStart
        if (now - evidence <= maxGapMillis) return record

        val settled = record.activeMillisBase + (evidence - segStart).coerceAtLeast(0L)
        return record.copy(
            activeMillisBase = settled,
            segmentStartedAt = now,
            segmentIndex = record.segmentIndex + 1
        )
    }

    /** 崩溃后按已落盘的点位重算路程，与在线累计口径一致（含跳点剔除）。 */
    fun recomputeDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            if (a.seg != b.seg) continue
            val d = Geo.haversineMeters(a.lat, a.lon, b.lat, b.lon)
            if (d < MIN_STEP_METERS) continue
            // 在线时跳点不计里程，重算也必须跳过，否则恢复后里程对不上。
            val dtSeconds = ((b.t - a.t).coerceAtLeast(1L)) / 1000.0
            if (d / dtSeconds > MAX_SPEED_MPS) continue
            total += d
        }
        return total
    }
}
