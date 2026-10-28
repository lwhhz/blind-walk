package com.blindwalk.app.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** 液态玻璃总开关。关掉时所有 liquidGlass() 都是空操作，控件退回原来的实色。 */
val LocalGlassEnabled = staticCompositionLocalOf { false }

/** 背景层（纸底 + 壁纸）被录进这张 GraphicsLayer，玻璃控件从这里采样。 */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 玻璃底色的通用透明度：和「窗纸」按钮保持一致。 */
const val GLASS_TINT_ALPHA = 0.62f

/**
 * 给任意控件套一层液态玻璃（Kyant0/AndroidLiquidGlass）。
 *
 * 原理：先把背景录进 GraphicsLayer，控件绘制时按自身形状取出来，
 * 经 模糊 → 折射(lens) → 提饱和 之后贴在自己下面。
 *
 * [pressable] 打开后额外做两件事，都严格限制在控件范围内：
 *  1. 按压点冒一圈白色柔光（画在 onDrawSurface，位于玻璃之上、文字之下）；
 *  2. 按压点周围一圈环形放大（在 onDrawBackdrop 里把背景用 clipPath 裁成圆环再放大重绘），
 *     这是"玻璃被按下去"的关键——只有放大没有高光会显得脏，只有高光没有放大又像贴纸。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.Transparent,
    blurRadius: Float = 14f,
    refractionHeight: Float = 22f,
    refractionAmount: Float = 44f,
    highlight: Highlight? = Highlight.Default,
    shadow: Shadow? = null,
    pressable: Boolean = true
): Modifier {
    if (!LocalGlassEnabled.current) return this
    val backdrop = LocalGlassBackdrop.current ?: return this

    // 按压位置与进度：位置在 composition 里读，才能保证每次变化都触发重绘。
    var pressPoint by remember { mutableStateOf<Offset?>(null) }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressPoint != null) 1f else 0f,
        animationSpec = tween(180),
        label = "glassPress"
    )

    val center = pressPoint
    val progress = pressProgress

    val glass = this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            blur(blurRadius)
            lens(
                refractionHeight = refractionHeight,
                refractionAmount = refractionAmount,
                depthEffect = true,
                chromaticAberration = true
            )
            vibrancy()
        },
        highlight = { highlight },
        shadow = { shadow },
        onDrawBackdrop = { drawBackdrop ->
            drawBackdrop()
            // 圆环放大：只有这一圈被放大，看起来像玻璃表面被按出一个凹坑。
            if (pressable && center != null && progress > 0.01f) {
                drawPressRing(center, progress) { drawBackdrop() }
            }
        },
        onDrawSurface = {
            if (tint.alpha > 0.01f) drawRect(tint)
            // 按压点柔光：叠在玻璃之上、文字之下。
            if (pressable && center != null && progress > 0.01f) {
                drawPressGlow(center, progress)
            }
        }
    )

    if (!pressable) return glass

    return glass.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pressPoint = down.position
            // 用 Final 阶段观察：不消费事件，点击、拖拽、滑动都照常工作。
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                pressPoint = change.position
            }
            pressPoint = null
        }
    }
}

/** 小尺寸控件（胶囊、标签）用更薄的折射，否则边缘会糊成一团。 */
@Composable
fun Modifier.liquidGlassChip(
    shape: Shape,
    tint: Color,
    pressable: Boolean = true
): Modifier = liquidGlass(
    shape = shape,
    tint = tint,
    blurRadius = 10f,
    refractionHeight = 14f,
    refractionAmount = 28f,
    pressable = pressable
)

private fun DrawScope.drawPressGlow(center: Offset, progress: Float) {
    // 柔光铺满整个控件并向边缘淡出：按压时整块玻璃"被点亮"。
    val radius = size.minDimension * 1.05f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.72f * progress),
            0.40f to Color.White.copy(alpha = 0.30f * progress),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** 环半径随按压从 0.50 涨到 0.78，读起来像"荡开一圈"。 */
private fun pressRingOuter(scope: DrawScope, progress: Float): Float =
    scope.size.minDimension * (0.50f + 0.28f * progress)

private fun DrawScope.drawPressRing(
    center: Offset,
    progress: Float,
    drawContent: DrawScope.() -> Unit
) {
    val outer = pressRingOuter(this, progress)
    // 内圈只有外圈的 35%，扭曲带很宽，一眼能看出"凹下去"。
    val inner = outer * 0.35f

    val ring = Path().apply {
        val o = Path().apply {
            addOval(Rect(center = center, radius = outer))
        }
        val i = Path().apply {
            addOval(Rect(center = center, radius = inner))
        }
        op(o, i, PathOperation.Difference)
    }

    // 环内把背景放大 52%，折射更夸张。
    clipPath(ring) {
        val s = 1f + 0.52f * progress
        scale(scaleX = s, scaleY = s, pivot = center) {
            drawContent()
        }
    }
}

val GlassDefaultRadius = 18.dp

/**
 * 自绘的高光边：单独一层叠在玻璃卡片上。
 *
 * 为什么不用库自带的 Highlight：它跟控件共用同一个 shape。
 * 而滑动行的卡片为了和直角的动作按钮贴合，形状是矩形，
 * 高光于是也变成一圈矩形的边，圆角处会缺一块。
 *
 * 这层按绘制时的偏移量决定四个角：
 *  - 外侧（贴着容器边缘那一侧）保持圆角，跟容器的裁切吻合；
 *  - 接缝那一侧收成直角，和直角动作按钮对齐。
 * 偏移量在绘制阶段读取，滑动时逐帧更新，不触发重组。
 */
fun Modifier.liquidGlassRim(
    cornerRadius: Dp,
    dx: () -> Float,
    width: Dp = 0.6.dp,
    color: Color = Color.White
): Modifier = drawBehind {
    val stroke = width.toPx()
    val half = stroke / 2f
    val r = (cornerRadius.toPx() - half).coerceAtLeast(0f)
    val offset = dx()

    val startR = if (offset > 0f) 0f else r
    val endR = if (offset < 0f) 0f else r

    // 路径向内缩半个描边，保证高光整条落在卡片内部，不会溢出到动作按钮上。
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = half,
                top = half,
                right = size.width - half,
                bottom = size.height - half,
                topLeftCornerRadius = CornerRadius(startR),
                topRightCornerRadius = CornerRadius(endR),
                bottomRightCornerRadius = CornerRadius(endR),
                bottomLeftCornerRadius = CornerRadius(startR)
            )
        )
    }

    // 和库里 Highlight.Default 一致的观感：顶边最亮、往下衰减，加法混合。
    // 之前用的是从左上角斜向右下的渐变——行卡片又宽又扁，横向距离占绝对主导，
    // 结果整条边只有最左边亮，像是只在左侧描了一道。
    // 改成竖向渐变后，顶边整体亮、底边收暗，左右两边自上而下自然变淡。
    val brush = Brush.linearGradient(
        colors = listOf(
            color.copy(alpha = 0.50f),
            color.copy(alpha = 0.22f),
            color.copy(alpha = 0.06f)
        ),
        start = Offset(0f, 0f),
        end = Offset(0f, size.height)
    )
    drawPath(path, brush, style = Stroke(stroke), blendMode = BlendMode.Plus)
}
