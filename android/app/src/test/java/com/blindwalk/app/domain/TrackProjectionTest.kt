package com.blindwalk.app.domain

import com.blindwalk.app.domain.geo.TrackProjection
import com.blindwalk.app.domain.model.LatLon
import com.blindwalk.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackProjectionTest {

    private val left = 72f
    private val top = 236f
    private val right = 1008f
    private val bottom = 940f
    private val padding = 56f

    @Test
    fun `points stay inside the padded rect`() {
        val points = listOf(
            LatLon(31.2300, 121.4700),
            LatLon(31.2320, 121.4760),
            LatLon(31.2280, 121.4780)
        )
        val offsets = TrackProjection.project(points, left, top, right, bottom, padding)

        assertEquals(points.size, offsets.size)
        offsets.forEach { o ->
            assertTrue("x=$o", o.x >= left + padding - 0.5f && o.x <= right - padding + 0.5f)
            assertTrue("y=$o", o.y >= top + padding - 0.5f && o.y <= bottom - padding + 0.5f)
        }
    }

    @Test
    fun `north is up`() {
        val points = listOf(LatLon(31.0, 121.0), LatLon(31.01, 121.0))
        val offsets = TrackProjection.project(points, left, top, right, bottom, padding)
        assertTrue("northern point should be higher on screen", offsets[1].y < offsets[0].y)
    }

    @Test
    fun `single point is centered without crashing`() {
        val offsets = TrackProjection.project(listOf(LatLon(31.0, 121.0)), left, top, right, bottom, padding)
        assertEquals(1, offsets.size)
        assertTrue(offsets[0].x.isFinite() && offsets[0].y.isFinite())
    }

    @Test
    fun `duplicate points do not blow up`() {
        val same = LatLon(31.0, 121.0)
        val offsets = TrackProjection.project(listOf(same, same, same), left, top, right, bottom, padding)
        assertEquals(3, offsets.size)
        offsets.forEach { assertTrue(it.x.isFinite() && it.y.isFinite()) }
    }

    @Test
    fun `segments split at pause boundary`() {
        val points = listOf(
            TrackPoint(1, 31.0, 121.0, 5f, seg = 0),
            TrackPoint(2, 31.0, 121.001, 5f, seg = 0),
            TrackPoint(3, 31.0, 121.002, 5f, seg = 1),
            TrackPoint(4, 31.0, 121.003, 5f, seg = 1)
        )
        val segments = TrackProjection.splitIntoSegments(points)
        assertEquals(2, segments.size)
        assertEquals(listOf(0, 1), segments[0])
        assertEquals(listOf(2, 3), segments[1])
    }
}
