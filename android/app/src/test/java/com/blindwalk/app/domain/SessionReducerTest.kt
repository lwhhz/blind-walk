package com.blindwalk.app.domain

import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.domain.session.SessionReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReducerTest {

    private fun started(now: Long = 1_000L) = SessionReducer.start("session-1", now)

    @Test
    fun `start begins a running segment`() {
        val s = started(1_000L)
        assertEquals(SessionStatus.RUNNING, s.status)
        assertEquals(1_000L, s.segmentStartedAt)
        assertEquals(0.0, s.distanceMeters, 0.0)
    }

    @Test
    fun `pause freezes elapsed time`() {
        val paused = SessionReducer.pause(started(0L), 5_000L)
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertEquals(5_000L, paused.activeMillis(60_000L))
        assertNull(paused.segmentStartedAt)
    }

    @Test
    fun `resume continues accumulating and starts a new segment`() {
        val paused = SessionReducer.pause(started(0L), 5_000L)
        val resumed = SessionReducer.resume(paused, 10_000L)
        assertEquals(1, resumed.segmentIndex)
        assertEquals(8_000L, resumed.activeMillis(13_000L))
    }

    @Test
    fun `stop from paused keeps accumulated time`() {
        val paused = SessionReducer.pause(started(0L), 5_000L)
        val ended = SessionReducer.stop(paused, 20_000L)
        assertEquals(SessionStatus.ENDED, ended.status)
        assertEquals(5_000L, ended.activeMillis(999_999L))
        assertEquals(5_000L, ended.activeMillisBase)
    }

    @Test
    fun `ended is terminal`() {
        val ended = SessionReducer.stop(started(0L), 1_000L)
        assertEquals(ended, SessionReducer.pause(ended, 2_000L))
        assertEquals(ended, SessionReducer.resume(ended, 2_000L))
        assertEquals(ended, SessionReducer.stop(ended, 2_000L))
        assertEquals(ended, SessionReducer.countInstruction(ended))
    }

    @Test
    fun `distance accumulates beyond the jitter threshold`() {
        val s0 = started()
        val first = SessionReducer.onLocation(s0, 1_000L, 31.2300, 121.4700, 5f)
        assertNotNull(first.point)
        val second = SessionReducer.onLocation(first.record, 2_000L, 31.2301, 121.4700, 5f)
        assertNotNull(second.point)
        assertTrue(second.record.distanceMeters > 10.0)
        assertEquals(0, second.point!!.seg)
    }

    @Test
    fun `gps jitter is ignored`() {
        val s0 = started()
        val first = SessionReducer.onLocation(s0, 1_000L, 31.2300, 121.4700, 5f)
        val second = SessionReducer.onLocation(first.record, 1_500L, 31.23000001, 121.47000001, 5f)
        assertNull(second.point)
        assertEquals(0.0, second.record.distanceMeters, 1e-9)
    }

    @Test
    fun `inaccurate fix is rejected`() {
        val s0 = started()
        val result = SessionReducer.onLocation(s0, 1_000L, 31.23, 121.47, 80f)
        assertNull(result.point)
        assertNull(result.record.lastLat)
    }

    @Test
    fun `teleport is rejected`() {
        val s0 = started()
        val first = SessionReducer.onLocation(s0, 1_000L, 31.2300, 121.4700, 5f)
        // 约 1.1 km 位移，1 秒内发生 → 超速，丢弃
        val jump = SessionReducer.onLocation(first.record, 2_000L, 31.2400, 121.4700, 5f)
        assertNull(jump.point)
    }

    @Test
    fun `paused session ignores locations`() {
        val paused = SessionReducer.pause(started(0L), 1_000L)
        val result = SessionReducer.onLocation(paused, 2_000L, 31.23, 121.47, 5f)
        assertNull(result.point)
    }

    // ------------------------------------------------ 锚点门限（静止漂移）

    @Test
    fun `standing still does not accumulate distance`() {
        // 精度 ±5m 时门限 = max(5, 5×0.6) = 5m；抖动 0~3m 必须被吃掉。
        var record = SessionReducer.onLocation(started(0L), 1_000L, 31.2300, 121.4700, 5f).record
        val base = record.distanceMeters

        repeat(20) { i ->
            val jitter = (i % 4) * 0.00001 // 每次 0~3 米
            record = SessionReducer.onLocation(
                record, 1_000L + i * 1_000L, 31.2300 + jitter, 121.4700, 5f
            ).record
        }
        assertEquals(base, record.distanceMeters, 1e-9)
    }

    @Test
    fun `walking beyond the gate is counted`() {
        var record = SessionReducer.onLocation(started(0L), 1_000L, 31.2300, 121.4700, 5f).record
        // 0.0001 纬度 ≈ 11m，超过 5m 门限，且速度 11/5 = 2.2 m/s 合理
        val r = SessionReducer.onLocation(record, 6_000L, 31.2301, 121.4700, 5f)
        record = r.record
        assertNotNull(r.point)
        assertTrue(record.distanceMeters > 10.0)
    }

    @Test
    fun `teleport is not counted but re-anchors`() {
        var record = SessionReducer.onLocation(started(0L), 1_000L, 31.2300, 121.4700, 5f).record
        // 1.1km / 1s → 超速，不计里程
        val jump = SessionReducer.onLocation(record, 2_000L, 31.2400, 121.4700, 5f)
        assertNull(jump.point)
        assertEquals(0.0, jump.record.distanceMeters, 1e-9)
        // 但锚点已挪到新位置：紧接着的正常移动应能继续记录
        val next = SessionReducer.onLocation(jump.record, 12_000L, 31.2401, 121.4700, 5f)
        assertNotNull(next.point)
        assertTrue(next.record.distanceMeters > 10.0)
    }

    @Test
    fun `recomputeDistance skips segment breaks`() {
        // 时间戳必须是真实量级（相隔 1 秒），否则速度判据会把正常的点当成跳点。
        val points = listOf(
            com.blindwalk.app.domain.model.TrackPoint(1_000, 31.2300, 121.4700, 5f, 0),
            com.blindwalk.app.domain.model.TrackPoint(2_000, 31.2301, 121.4700, 5f, 0),
            com.blindwalk.app.domain.model.TrackPoint(3_000, 31.2400, 121.4700, 5f, 1)
        )
        val d = SessionReducer.recomputeDistance(points)
        assertTrue("expected only the first leg, got $d", d in 10.0..12.0)
    }

    // ------------------------------------------------ 崩溃恢复（RECOVERY_GAP_MS）

    private val gap = 120_000L

    @Test
    fun `short downtime keeps the segment continuous`() {
        val live = started(0L).copy(lastFixAt = 10_000L)
        val now = 10_000L + 60_000L
        val recovered = SessionReducer.recoverAfterCrash(live, now, gap)
        assertEquals(live, recovered)
        // 60 秒空档在容忍范围内，仍计入时长
        assertEquals(70_000L, recovered.activeMillis(now))
    }

    @Test
    fun `long downtime is excluded from active time`() {
        val live = started(0L).copy(lastFixAt = 10_000L)
        val now = 10_000L + 600_000L // App 死了 10 分钟
        val recovered = SessionReducer.recoverAfterCrash(live, now, gap)

        assertEquals(10_000L, recovered.activeMillisBase)
        assertEquals(now, recovered.segmentStartedAt)
        assertEquals(1, recovered.segmentIndex) // 轨迹在此断开
        assertEquals(10_000L, recovered.activeMillis(now)) // 不把空档算进来
    }

    @Test
    fun `missing segment start is repaired`() {
        val broken = started(0L).copy(segmentStartedAt = null, lastFixAt = 5_000L)
        val recovered = SessionReducer.recoverAfterCrash(broken, 90_000L, gap)
        assertEquals(90_000L, recovered.segmentStartedAt)
        assertEquals(1, recovered.segmentIndex)
        assertTrue("时长不应瞬间暴涨", recovered.activeMillis(90_000L) < 1_000L)
    }

    @Test
    fun `ended sessions are untouched by recovery`() {
        val ended = SessionReducer.stop(started(0L), 5_000L)
        assertEquals(ended, SessionReducer.recoverAfterCrash(ended, 9_999_999L, gap))
    }
}
