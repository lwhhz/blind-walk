package com.blindwalk.app.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.util.lerp
import com.blindwalk.app.ui.nav.DampedDragAnimation
import com.blindwalk.app.ui.theme.BWTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest

/*
 * 液态玻璃开关 / 滑块。
 *
 * 外观和动画照搬 AndroidLiquidGlass-kmp 的 catalog（LiquidToggle / LiquidSlider）：
 *   开关轨道 64×28、滑块轨道 6dp、拇指 40×24、按下放大 1.5；
 *   按下时白色表面退去，露出折射 + 色散 + 高光 + 内阴影。
 *
 * 手势部分改成了 Compose 标准识别：范例的自研识别器在可滚动页面里会被外层吞掉事件。
 */

private val CapsuleShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/** 滑块。双向模式下填充从中点起算（遮罩滑块需要）。 */
@Composable
internal fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    bipolar: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val palette = BWTheme.colors
    val accentColor = palette.vermilion
    val trackColor = palette.rule.copy(alpha = 0.55f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()

        val animation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> }
            )
        }

        // 必须读 State：直接捕获参数的话，外部改值（点「原样」、纸色吸附）不会生效。
        val latestValue by rememberUpdatedState(value)
        LaunchedEffect(animation) {
            snapshotFlow { latestValue }
                .collectLatest { v ->
                    if (animation.targetValue != v) animation.updateValue(v)
                }
        }

        // 轨道 + 填充
        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(CapsuleShape)
                    .background(trackColor)
                    .height(6.dp)
                    .fillMaxWidth()
            )

            val progress = animation.progress
            val fillStart = if (bipolar) minOf(0.5f, progress) else 0f
            val fillSpan = if (bipolar) kotlin.math.abs(progress - 0.5f) else progress
            Box(
                Modifier
                    .graphicsLayer { translationX = trackWidth * fillStart }
                    .clip(CapsuleShape)
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val w = (constraints.maxWidth * fillSpan).fastRoundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    }
            )
        }

        // 拇指：只负责显示。位置用 layout 而不是 graphicsLayer，
        // 保证触摸热区和绘制位置一致。
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val w = placeable.width.toFloat()
                    val x = (-w / 2f + trackWidth * animation.progress)
                        .fastCoerceIn(-w / 4f, trackWidth - w * 3f / 4f)
                    val xPos = if (isLtr) x else trackWidth - x - w
                    layout(constraints.maxWidth, placeable.height) {
                        placeable.place(xPos.toInt(), 0)
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val p = animation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, p)
                            val scaleY = lerp(0f, 1f, p)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { CapsuleShape },
                    effects = {
                        val p = animation.pressProgress
                        blur(8f.dp.toPx() * (1f - p))
                        lens(
                            refractionHeight = 10f.dp.toPx() * p,
                            refractionAmount = 14f.dp.toPx() * p,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = animation.pressProgress
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val p = animation.pressProgress
                        InnerShadow(
                            radius = 4.dp * p,
                            color = Color.Black.copy(alpha = 0.15f * p)
                        )
                    },
                    layerBlock = {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                        val velocity = animation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                    }
                )
                .size(ThumbWidth, ThumbHeight)
        )

        // 交互层：整行接管，按绝对坐标算值。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(valueRange, isLtr) {
                    fun valueAt(x: Float): Float {
                        val f = (x / size.width.toFloat()).coerceIn(0f, 1f)
                        val span = valueRange.endInclusive - valueRange.start
                        return (valueRange.start + span * (if (isLtr) f else 1f - f))
                            .coerceIn(valueRange)
                    }

                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            animation.press()
                            val v = valueAt(offset.x)
                            animation.updateValue(v)
                            onValueChange(v)
                        },
                        onDragEnd = {
                            onValueChange(animation.targetValue)
                            onValueChangeFinished?.invoke()
                            animation.release()
                        },
                        onDragCancel = { animation.release() }
                    ) { change, _ ->
                        change.consume()
                        val v = valueAt(change.position.x)
                        animation.updateValue(v)
                        onValueChange(v)
                    }
                }
                .pointerInput(valueRange, isLtr) {
                    detectTapGestures { offset ->
                        val span = valueRange.endInclusive - valueRange.start
                        val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val target = (valueRange.start + span * (if (isLtr) f else 1f - f))
                            .coerceIn(valueRange)
                        animation.animateToValue(target)
                        onValueChange(target)
                        onValueChangeFinished?.invoke()
                    }
                }
        )
    }
}

/** 开关。尺寸、动画、按压形变与范例一致。 */
@Composable
internal fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val palette = BWTheme.colors
    val accentColor = palette.vermilion
    val trackColor = palette.ink.copy(alpha = 0.10f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val animation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }
    LaunchedEffect(animation) {
        snapshotFlow { fraction }.collectLatest { animation.updateValue(it) }
    }
    val latestChecked by rememberUpdatedState(checked)
    LaunchedEffect(animation) {
        snapshotFlow { latestChecked }
            .collectLatest { on ->
                val target = if (on) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    animation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CapsuleShape)
                .drawBehind {
                    drawRect(lerpColor(trackColor, accentColor, animation.value))
                }
                .size(64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, animation.value)
                        else lerp(-padding, -(padding + dragWidth), animation.value)
                }
                .semantics { role = Role.Switch }
                .pointerInput(checked) {
                    detectTapGestures {
                        val target = !checked
                        fraction = if (target) 1f else 0f
                        onCheckedChange(target)
                    }
                }
                .pointerInput(dragWidth) {
                    var dragged = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragged = false
                            animation.press()
                        },
                        onDragEnd = {
                            fraction = if (animation.targetValue >= 0.5f) 1f else 0f
                            if (dragged) onCheckedChange(fraction == 1f)
                            animation.release()
                        },
                        onDragCancel = { animation.release() }
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount != 0f) dragged = true
                        val delta = dragAmount / dragWidth
                        fraction =
                            if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                            else (fraction - delta).fastCoerceIn(0f, 1f)
                        animation.updateValue(fraction)
                    }
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val p = animation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, p)
                            val scaleY = lerp(0f, 0.75f, p)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { CapsuleShape },
                    effects = {
                        val p = animation.pressProgress
                        blur(8f.dp.toPx() * (1f - p))
                        lens(
                            refractionHeight = 5f.dp.toPx() * p,
                            refractionAmount = 10f.dp.toPx() * p,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = animation.pressProgress
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val p = animation.pressProgress
                        InnerShadow(
                            radius = 4.dp * p,
                            color = Color.Black.copy(alpha = 0.15f * p)
                        )
                    },
                    layerBlock = {
                        scaleX = animation.scaleX
                        scaleY = animation.scaleY
                        val velocity = animation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                    }
                )
                .size(ThumbWidth, ThumbHeight)
        )
    }
}
