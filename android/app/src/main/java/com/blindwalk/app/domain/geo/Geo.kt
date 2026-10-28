package com.blindwalk.app.domain.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {

    const val EARTH_RADIUS_M: Double = 6_371_008.8

    private const val DEG: Double = PI / 180.0

    /** 球面大圆距离（haversine），单位米。 */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = (lat2 - lat1) * DEG
        val dLon = (lon2 - lon1) * DEG
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val a = sinLat * sinLat + cos(lat1 * DEG) * cos(lat2 * DEG) * sinLon * sinLon
        val c = 2.0 * asin(min(1.0, sqrt(a)))
        return EARTH_RADIUS_M * c
    }
}
