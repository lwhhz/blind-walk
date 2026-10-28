package com.blindwalk.app.domain.geo

import androidx.compose.ui.geometry.Offset
import com.blindwalk.app.domain.model.LatLon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 把经纬度压平到一块矩形里：局部切平面近似（米），等比缩放，北朝上。
 * 不接地图 SDK，所以这里没有任何投影坐标系/纠偏逻辑——只是画形状。
 */
object TrackProjection {

    private const val METERS_PER_DEG_LAT = 110_540.0
    private const val METERS_PER_DEG_LON_AT_EQUATOR = 111_320.0

    fun project(
        points: List<LatLon>,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        padding: Float
    ): List<Offset> {
        if (points.isEmpty()) return emptyList()

        val refLat = points.first().lat
        val refLon = points.first().lon
        val cosLat = cos(Math.toRadians(refLat))

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        val local = ArrayList<Pair<Double, Double>>(points.size)
        for (p in points) {
            val x = (p.lon - refLon) * METERS_PER_DEG_LON_AT_EQUATOR * cosLat
            val y = (p.lat - refLat) * METERS_PER_DEG_LAT
            local.add(x to y)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        val availW = max(1f, (right - left) - padding * 2f)
        val availH = max(1f, (bottom - top) - padding * 2f)

        val scale = when {
            spanX < 1e-6 && spanY < 1e-6 -> 0.0
            spanX < 1e-6 -> availH / spanY
            spanY < 1e-6 -> availW / spanX
            else -> min(availW / spanX, availH / spanY)
        }

        val drawnW = spanX * scale
        val drawnH = spanY * scale
        val originX = left + padding + (availW - drawnW) / 2.0
        val originY = top + padding + (availH - drawnH) / 2.0

        return local.map { (x, y) ->
            val sx = originX + (x - minX) * scale
            // 屏幕 y 向下，地理 y 向上：翻转。
            val sy = originY + (maxY - y) * scale
            Offset(sx.toFloat(), sy.toFloat())
        }
    }

    /** 按 seg 切段：暂停恢复后不连线。单点段返回长度为 1 的列表。 */
    fun splitIntoSegments(points: List<com.blindwalk.app.domain.model.TrackPoint>): List<List<Int>> {
        val result = ArrayList<List<Int>>()
        var current = ArrayList<Int>()
        var lastSeg = Int.MIN_VALUE
        points.forEachIndexed { index, p ->
            if (p.seg != lastSeg) {
                if (current.isNotEmpty()) result.add(current)
                current = ArrayList()
                lastSeg = p.seg
            }
            current.add(index)
        }
        if (current.isNotEmpty()) result.add(current)
        return result
    }
}
