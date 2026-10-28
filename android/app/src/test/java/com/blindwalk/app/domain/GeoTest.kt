package com.blindwalk.app.domain

import com.blindwalk.app.domain.geo.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun `one degree of longitude at equator is about 111 km`() {
        val d = Geo.haversineMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_194.9, d, 5.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = Geo.haversineMeters(31.2304, 121.4737, 31.2400, 121.4800)
        val b = Geo.haversineMeters(31.2400, 121.4800, 31.2304, 121.4737)
        assertEquals(a, b, 1e-6)
    }

    @Test
    fun `same point is zero`() {
        assertEquals(0.0, Geo.haversineMeters(39.9, 116.4, 39.9, 116.4), 1e-9)
    }

    @Test
    fun `short step is roughly linear`() {
        // 上海纬度上 0.0001 经度 ≈ 9.5 m
        val d = Geo.haversineMeters(31.2304, 121.4737, 31.2304, 121.4738)
        assertTrue("unexpected: $d", d in 8.0..11.0)
    }
}
