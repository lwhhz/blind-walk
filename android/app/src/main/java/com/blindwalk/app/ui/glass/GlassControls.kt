package com.blindwalk.app.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.blindwalk.app.ui.theme.BWTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** 胶囊：宽度一半的圆角。 */
private val Capsule = RoundedCornerShape(percent = 50)

private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/** 按下时拇指的放大倍数。参考实现用的就是 1.5，这是"被捏住"的主要视觉线索。 */
private const val PRESSED_SCALE = 1.5f

/**
 * 液态玻璃滑块。
 *
 * 手势分两处（参考实现的做法）：
 *  - 轨道上点按 = 直接按比例设值（这里拿到的是轨道坐标）；
 *  - 拇指上拖动 = 读**位移增量**换算成值（拇指自己的坐标里没有轨道宽度信息）。
 *
 * 拇指静止时是不透明白色胶囊，按下白色退去露出玻璃，同时放大到 1.5 倍并压扁采样到的轨道。
 */
@Composable
fun BwSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 双向滑块（比如遮罩 -80..80）：填充从中点起算，而不是从左端。 */
    bipolar: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val glass = LocalGlassEnabled.current
    val pageBackdrop = LocalGlassBackdrop.current
    // 玻璃模式下直接用照搬范例的实现
    if (glass && pageBackdrop != null) {
        LiquidSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            backdrop = pageBackdrop,
            modifier = modifier,
            bipolar = bipolar,
            onValueChangeFinished = onValueChangeFinished
        )
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current

    val trackColor = BWTheme.colors.rule.copy(alpha = 0.55f)
    val accent = BWTheme.colors.vermilion

    var pressed by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 700f),
        label = "sliderPress"
    )

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it != 0f } ?: 1f

    // 手势协程只启动一次，必须让它总是调用最新的回调，否则会写到过期的状态上。
    val latestValueChange by rememberUpdatedState(onValueChange)
    val latestFinished by rememberUpdatedState(onValueChangeFinished)

    // 拇指位置用 Animatable 自己驱动：
    // 按下时直接 snapTo（严格跟手），松手/吸附时才 animateTo。
    // 之前用 animateFloatAsState + 随 pressed 切换 animationSpec，
    // 第二次操作开始会出现"拖动时不动、松手才跳过去"的问题。
    val pos = remember { Animatable(fractionOf(value, valueRange)) }
    LaunchedEffect(value, pressed, valueRange) {
        val target = fractionOf(value, valueRange)
        if (pressed) pos.snapTo(target) else pos.animateTo(target, spring(0.8f, 400f))
    }
    val fraction = pos.value

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val travelPx = (trackWidthPx - with(density) { ThumbWidth.toPx() }).coerceAtLeast(1f)

        fun valueFromX(x: Float): Float {
            val f = (x / trackWidthPx).coerceIn(0f, 1f)
            return (valueRange.start + span * (if (isLtr) f else 1f - f)).coerceIn(valueRange)
        }

        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule)
                .height(8.dp)
                .fillMaxWidth()
        ) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(trackColor))
            // 填充段：双极从中点画到拇指，单极从左端画到拇指。
            Canvas(Modifier.height(8.dp).fillMaxWidth()) {
                val thumbX = size.width * fraction
                if (bipolar) {
                    val mid = size.width / 2f
                    val left = minOf(mid, thumbX)
                    val right = maxOf(mid, thumbX)
                    drawRect(
                        color = accent,
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, size.height)
                    )
                } else {
                    drawRect(color = accent, size = Size(thumbX, size.height))
                }
            }
        }

        // 手势挂在轨道容器上（它不会移动），用绝对坐标算值。
        // 之前挂在拇指上：拇指是跟着值移动的，指针坐标随拇指移动而反向偏移，
        // 位移被抵消掉，拖不动，而且按住状态卡住不复位。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(enabled, span, isLtr) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        pressed = true
                        latestValueChange(valueFromX(down.position.x))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            latestValueChange(valueFromX(change.position.x))
                            change.consume()
                        }
                        pressed = false
                        latestFinished?.invoke()
                    }
                }
        )

        Box(
            Modifier
                .graphicsLayer {
                    translationX = travelPx * fraction
                    val s = 1f + (PRESSED_SCALE - 1f) * pressProgress
                    scaleX = s
                    scaleY = s
                }
                .then(thumbGlass(pageBackdrop, trackBackdrop, pressProgress, glass, slider = true))
                .size(ThumbWidth, ThumbHeight)
        )
    }
}

/**
 * 液态玻璃开关。
 *
 * 尺寸与参考实现一致：轨道 64×28、拇指 40×24、左右各留 2dp，
 * 所以拇指两端距轨道边缘都是 2dp——对称。
 */
@Composable
fun BwSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val glass = LocalGlassEnabled.current
    val pageBackdrop = LocalGlassBackdrop.current
    if (glass && pageBackdrop != null) {
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            backdrop = pageBackdrop,
            modifier = modifier
        )
        return
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current

    val trackColor = BWTheme.colors.rule.copy(alpha = 0.45f)
    val accent = BWTheme.colors.vermilion

    var pressed by remember { mutableStateOf(false) }
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 700f),
        label = "switchPress"
    )

    // 拖动中的实时位置；松手吸附到 0 或 1。
    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val settled by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "switchFraction"
    )
    val fraction = if (pressed) dragFraction else settled

    val trackBackdrop = rememberLayerBackdrop()
    val width = 64.dp
    val height = 28.dp
    val inset = 2.dp
    val travel = width - ThumbWidth - inset * 2

    Box(
        modifier
            .size(width, height)
            .pointerInput(enabled, checked, travel) {
                if (!enabled) return@pointerInput
                val travelPx = with(density) { travel.toPx() }
                val insetPx = with(density) { inset.toPx() }
                val tapSlop = with(density) { 8.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    dragFraction = if (checked) 1f else 0f
                    var moved = 0f
                    var lastX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        moved += kotlin.math.abs(change.position.x - lastX)
                        lastX = change.position.x
                        // 只在真正拖动后才改位置：单纯点一下时拇指不该先跳一下。
                        if (moved > tapSlop) {
                            val raw = ((change.position.x - insetPx) / travelPx).coerceIn(0f, 1f)
                            dragFraction = if (isLtr) raw else 1f - raw
                        }
                        change.consume()
                    }
                    pressed = false
                    val target = if (moved <= tapSlop) !checked else dragFraction >= 0.5f
                    onCheckedChange(target)
                }
            }
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .size(width, height)
                .clip(Capsule)
                // 轨道颜色随位置插值，而不是到终点才跳变。
                .drawBehind {
                    drawRect(lerp(trackColor, accent.copy(alpha = 0.85f), fraction))
                }
        )

        val offsetX = if (isLtr) inset + travel * fraction else width - inset - ThumbWidth - travel * fraction

        Box(
            Modifier
                // 纵向也要留出 2dp：容器默认是 TopStart，不加这一行拇指会贴在顶部，
                // 上下边距变成 0 / 4dp，看起来就是"不对称"。
                .offset(x = offsetX, y = (height - ThumbHeight) / 2)
                .graphicsLayer {
                    val s = 1f + (PRESSED_SCALE - 1f) * pressProgress
                    scaleX = s
                    scaleY = s
                }
                .then(thumbGlass(pageBackdrop, trackBackdrop, pressProgress, glass, slider = false))
                .size(ThumbWidth, ThumbHeight)
        )
    }
}

/**
 * 拇指外观。
 *
 * 玻璃模式下：采样「页面 + 轨道」的组合；按下时把采样到的轨道压扁（scaleY 0→0.75），
 * 看起来像拇指把底下的液面压出一个坑。静止时盖一层不透明白——这也正是
 * 参考实现的观感：白色胶囊，按下去才变成玻璃。
 */
@Composable
private fun thumbGlass(
    pageBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop?,
    trackBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    pressProgress: Float,
    glass: Boolean,
    slider: Boolean
): Modifier {
    if (!glass || pageBackdrop == null) {
        return Modifier
            .clip(Capsule)
            .background(BWTheme.colors.paper)
    }
    return Modifier.drawBackdrop(
        backdrop = rememberCombinedBackdrop(
            pageBackdrop,
            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                val p = pressProgress
                // 按下时轨道采样被压扁并放大，形成"液面被压下去"的错觉。
                val scaleX = lerpFloat(2f / 3f, if (slider) 1f else 0.75f, p)
                val scaleY = lerpFloat(0f, if (slider) 1f else 0.75f, p)
                scale(scaleX, scaleY) {
                    drawBackdrop()
                }
            }
        ),
        shape = { Capsule },
        effects = {
            val p = pressProgress
            blur((if (slider) 6f else 8f).dp.toPx() * (1f - p))
            lens(
                refractionHeight = (if (slider) 8f else 5f).dp.toPx() * p,
                refractionAmount = (if (slider) 12f else 10f).dp.toPx() * p,
                depthEffect = true,
                chromaticAberration = true
            )
        },
        highlight = { Highlight.Default },
        shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.10f)) },
        // 静止时盖一层不透明白（和参考实现一致）；按下后白色退去，玻璃显形。
        onDrawSurface = {
            drawRect(Color.White.copy(alpha = 1f - pressProgress))
        }
    )
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChangeX(): Float =
    position.x - previousPosition.x

/** 值 → 0..1 的位置。 */
internal fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = (range.endInclusive - range.start).takeIf { it != 0f } ?: 1f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}
