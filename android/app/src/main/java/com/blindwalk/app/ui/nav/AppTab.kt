package com.blindwalk.app.ui.nav

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 三个主 tab。顺序即底栏顺序，也是页面横移的方向依据。 */
enum class AppTab(val label: String) {
    HOME("首页"),
    TRIP("瞎走"),
    PAPER("窗纸")
}

/**
 * 底栏图标。用 Canvas 画几何线稿，不引图标库——
 * 三个形状都很简单，而且线稿和这套"纸与墨"的观感更搭。
 */
@Composable
fun TabGlyph(tab: AppTab, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)

        when (tab) {
            // 首页：屋顶 + 屋身
            AppTab.HOME -> {
                val roof = Path().apply {
                    moveTo(w * 0.14f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.46f)
                }
                drawPath(roof, color, style = stroke)
                val body = Path().apply {
                    moveTo(w * 0.24f, h * 0.42f)
                    lineTo(w * 0.24f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.42f)
                }
                drawPath(body, color, style = stroke)
            }

            // 瞎走：罗盘（圆 + 指针）
            AppTab.TRIP -> {
                drawCircle(
                    color = color,
                    radius = w * 0.36f,
                    center = Offset(w / 2f, h / 2f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.5f),
                    end = Offset(w * 0.68f, h * 0.32f),
                    strokeWidth = w * 0.10f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = color,
                    radius = w * 0.055f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
            }

            // 窗纸：窗框 + 中竖
            AppTab.PAPER -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.18f, h * 0.18f),
                    size = Size(w * 0.64f, h * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w * 0.5f, h * 0.2f),
                    end = Offset(w * 0.5f, h * 0.8f),
                    strokeWidth = w * 0.07f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** 图标尺寸统一在这里改。 */
val TabGlyphSize = 22.dp
