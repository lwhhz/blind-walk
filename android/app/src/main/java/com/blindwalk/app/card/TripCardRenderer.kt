package com.blindwalk.app.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.blindwalk.app.R
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.domain.geo.TrackProjection
import com.blindwalk.app.domain.model.LatLon
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.TrackPoint

/**
 * 总结卡片：1080×1440，纯 Canvas 绘制。
 *
 * 之所以不用 Compose 截图（GraphicsLayer / PixelCopy），是因为那类方案依赖
 * 视图已经完成一次真实绘制，在离屏、深色模式、字体缩放等场景下容易拿到空白图。
 * 直接画 Bitmap 是确定性的，尺寸与像素完全可控。
 *
 * 两套配色：纸白（默认，用于分享后在任何聊天背景上都清晰）
 * 与墨黑（夜里导出的版本）。版式完全相同，只换颜色。
 */
object TripCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1440

    private const val MARGIN = 72f
    private const val HEADER_RULE_Y = 196f
    private const val TRACK_TOP = 236f
    private const val TRACK_BOTTOM = 940f
    private const val TRACK_PADDING = 56f
    private const val STAT_BASELINE = 1092f
    private const val STAT_LABEL_BASELINE = 1152f
    private const val FOOTER_RULE_Y = 1236f
    private const val FOOTER_BASELINE = 1312f

    private const val COL_WIDTH = (WIDTH - MARGIN * 2) / 3f

    private data class CardPalette(
        val paper: Int,
        val ink: Int,
        val muted: Int,
        val rule: Int,
        val grid: Int,
        val vermilion: Int
    )

    private val LightCard = CardPalette(
        paper = Color.parseColor("#F6F2E9"),
        ink = Color.parseColor("#191918"),
        muted = Color.parseColor("#8A8378"),
        rule = Color.parseColor("#DCD5C6"),
        grid = Color.parseColor("#EAE4D5"),
        vermilion = Color.parseColor("#C8452D")
    )

    private val DarkCard = CardPalette(
        paper = Color.parseColor("#141413"),
        ink = Color.parseColor("#F2EDE3"),
        muted = Color.parseColor("#9C958B"),
        rule = Color.parseColor("#34302B"),
        grid = Color.parseColor("#262320"),
        vermilion = Color.parseColor("#E0603F")
    )

    fun render(
        context: Context,
        record: SessionRecord,
        points: List<TrackPoint>,
        dark: Boolean = false
    ): Bitmap {
        val palette = if (dark) DarkCard else LightCard
        // 打包字体缺失时回落到系统衬线，不会崩。
        val serif = runCatching {
            ResourcesCompat.getFont(context, R.font.noto_serif_sc_regular)
        }.getOrNull() ?: Typeface.SERIF
        // 只有 Regular 一个文件，粗体交给系统合成。
        val serifBold = Typeface.create(serif, Typeface.BOLD)

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.paper)

        val p = Painter(canvas, palette, serif, serifBold)
        p.drawHeader(record)
        p.drawTrack(points)
        p.drawStats(record)
        p.drawFooter(record)
        return bitmap
    }

    private class Painter(
        val canvas: Canvas,
        val palette: CardPalette,
        val serif: Typeface,
        val serifBold: Typeface
    ) {

        fun drawHeader(record: SessionRecord) {
            // 有自定义名就用自定义名（上限 12 字，12 × 48px 含字距仍落在 936px 内）。
            val label = record.displayTitleOrNull() ?: "瞎走"
            canvas.drawText(
                label,
                MARGIN,
                124f,
                text(50f, palette.ink, serifBold, tracking = 1.5f)
            )
            canvas.drawText(
                "BLIND WALK",
                MARGIN,
                162f,
                text(18f, palette.muted, serif, tracking = 7.5f)
            )

            canvas.drawText(
                Fmt.date(record.startedAt),
                WIDTH - MARGIN,
                120f,
                text(34f, palette.ink, serif, alignRight = true, tracking = 1f)
            )
            canvas.drawText(
                Fmt.weekday(record.startedAt),
                WIDTH - MARGIN,
                160f,
                text(24f, palette.muted, serif, alignRight = true, tracking = 2f)
            )

            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.rule
                strokeWidth = 2f
            }
            canvas.drawLine(MARGIN, HEADER_RULE_Y, WIDTH - MARGIN, HEADER_RULE_Y, rule)
        }

        fun drawTrack(points: List<TrackPoint>) {
            drawGrid()

            if (points.isEmpty()) {
                val empty = text(28f, palette.muted, serif, tracking = 3f)
                empty.textAlign = Paint.Align.CENTER
                canvas.drawText("没有记录到轨迹", WIDTH / 2f, (TRACK_TOP + TRACK_BOTTOM) / 2f, empty)
                return
            }

            val latLons = points.map { LatLon(it.lat, it.lon) }
            val offsets = TrackProjection.project(
                latLons, MARGIN, TRACK_TOP, WIDTH - MARGIN, TRACK_BOTTOM, TRACK_PADDING
            )

            val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.vermilion
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            TrackProjection.splitIntoSegments(points).forEach { segment ->
                if (segment.size >= 2) {
                    val path = Path()
                    val first = offsets[segment.first()]
                    path.moveTo(first.x, first.y)
                    for (i in 1 until segment.size) {
                        val o = offsets[segment[i]]
                        path.lineTo(o.x, o.y)
                    }
                    canvas.drawPath(path, pathPaint)
                } else if (segment.size == 1) {
                    val o = offsets[segment.first()]
                    canvas.drawCircle(o.x, o.y, 7f, pathPaint)
                }
            }

            drawMarker(offsets.first(), start = true)
            if (offsets.size > 1) drawMarker(offsets.last(), start = false)
        }

        private fun drawGrid() {
            val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.grid
                strokeWidth = 2f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(2f, 14f), 0f)
            }
            val left = MARGIN
            val right = WIDTH - MARGIN
            for (i in 1..3) {
                val x = left + (right - left) * i / 4f
                canvas.drawLine(x, TRACK_TOP, x, TRACK_BOTTOM, grid)
            }
            for (j in 1..3) {
                val y = TRACK_TOP + (TRACK_BOTTOM - TRACK_TOP) * j / 4f
                canvas.drawLine(left, y, right, y, grid)
            }
        }

        private fun drawMarker(at: androidx.compose.ui.geometry.Offset, start: Boolean) {
            val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.paper }
            if (start) {
                canvas.drawCircle(at.x, at.y, 19f, paper)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.ink
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }
                canvas.drawCircle(at.x, at.y, 13f, stroke)
            } else {
                canvas.drawCircle(at.x, at.y, 21f, paper)
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.vermilion }
                canvas.drawCircle(at.x, at.y, 13f, fill)
            }
        }

        fun drawStats(record: SessionRecord) {
            val endedAt = record.endedAt ?: record.startedAt
            val activeMs = record.activeMillis(endedAt)
            val (distValue, distUnit) = Fmt.distanceParts(record.distanceMeters)

            drawStat(MARGIN, Fmt.duration(activeMs), "", "总时长")
            drawStat(MARGIN + COL_WIDTH, distValue, distUnit, "总路程")
            drawStat(MARGIN + COL_WIDTH * 2, record.instructionCount.toString(), "次", "指路次数")
        }

        private fun drawStat(x: Float, value: String, unit: String, label: String) {
            // 数字用等宽，三列宽度才不会随数值跳动。
            val mono = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            val valuePaint = text(92f, palette.ink, mono, tracking = 1f)
            canvas.drawText(value, x, STAT_BASELINE, valuePaint)

            if (unit.isNotEmpty()) {
                val unitPaint = text(34f, palette.muted, mono)
                val width = valuePaint.measureText(value)
                canvas.drawText(unit, x + width + 10f, STAT_BASELINE, unitPaint)
            }

            canvas.drawText(
                label,
                x,
                STAT_LABEL_BASELINE,
                text(24f, palette.muted, serif, tracking = 3f)
            )
        }

        fun drawFooter(record: SessionRecord) {
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.rule
                strokeWidth = 2f
            }
            canvas.drawLine(MARGIN, FOOTER_RULE_Y, WIDTH - MARGIN, FOOTER_RULE_Y, rule)

            // 左下角：有步数就显示步数，没有（无计步器/未授权）才回落品牌名，
            // 避免角落空着。右下角固定是起止时间。
            val footerLeft = if (record.stepBaseline != null) {
                "${Fmt.steps(record.steps)} 步"
            } else {
                "瞎走 · BLIND WALK"
            }
            canvas.drawText(
                footerLeft,
                MARGIN,
                FOOTER_BASELINE,
                text(22f, palette.muted, serif, tracking = 3f)
            )

            val endedAt = record.endedAt ?: System.currentTimeMillis()
            canvas.drawText(
                "${Fmt.time(record.startedAt)} – ${Fmt.time(endedAt)}",
                WIDTH - MARGIN,
                FOOTER_BASELINE,
                text(22f, palette.muted, serif, alignRight = true, tracking = 2f)
            )
        }

        private fun text(
            size: Float,
            color: Int,
            typeface: Typeface,
            tracking: Float = 0f,
            alignRight: Boolean = false
        ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
            if (tracking != 0f) letterSpacing = tracking / size
            if (alignRight) textAlign = Paint.Align.RIGHT
            isSubpixelText = true
        }
    }
}
