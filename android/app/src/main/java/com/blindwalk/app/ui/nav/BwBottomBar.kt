package com.blindwalk.app.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.blindwalk.app.ui.glass.LocalGlassBackdrop
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.theme.BWTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import androidx.compose.ui.unit.DpOffset
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private val Capsule = RoundedCornerShape(percent = 50)
private val BarHeight = 64.dp
private val ItemHeight = 56.dp

/** 按住时图标放大到多少（参考实现的 1.2）。 */
private const val PRESSED_ICON_SCALE = 1.2f

/** 指示器按住时的放大倍数：参考实现是 78/56 ≈ 1.39 的宽度比。 */
private const val PRESSED_PILL_SCALE = 1.39f

@Composable
fun BwBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    tripRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassEnabled.current && LocalGlassBackdrop.current != null
    if (glass) {
GlassBottomTabs(
            selected = selected,
            onSelect = onSelect,
            backdrop = LocalGlassBackdrop.current!!,
            tripRunning = tripRunning,
            modifier = modifier
        )
    } else {
        MaterialBottomBar(selected, onSelect, tripRunning, modifier)
    }
}

// ---------------------------------------------------------------- Material

@Composable
private fun MaterialBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    tripRunning: Boolean,
    modifier: Modifier
) {
    val palette = BWTheme.colors
    val tabs = AppTab.entries
    // 指示器滑动：弹簧收尾，不跟手时也不会一顿一顿。
    val position by animateFloatAsState(
        targetValue = selected.ordinal.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 480f),
        label = "barIndicator"
    )

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(BarHeight)
            .shadow(10.dp, Capsule, clip = false)
            .clip(Capsule)
            .background(palette.paper.copy(alpha = 0.94f))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / tabs.size

            // 指示器：一个比条目略窄的胶囊，在条目之间滑过去。
            Box(
                Modifier
                    .offset(x = itemWidth * position)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .clip(Capsule)
                    .background(palette.vermilion.copy(alpha = 0.13f))
            )

            Row(Modifier.fillMaxSize()) {
                tabs.forEach { tab ->
                    val active = tab == selected
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(tab) },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TabItemContent(
                            tab = tab,
                            active = active,
                            tripRunning = tripRunning,
                            iconScale = if (active) 1.06f else 1f
                        )
                    }
                }
            }
        }
    }
}


@Composable
internal fun TabItemContent(
    tab: AppTab,
    active: Boolean,
    tripRunning: Boolean,
    iconScale: Float,
    // 液态玻璃底栏用：强制图标/文字颜色（深色壁纸下灰色会糊）
    forceColor: Color? = null
) {
    val palette = BWTheme.colors
    val tint = forceColor ?: if (active) palette.ink else palette.muted
    Box {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TabGlyph(
                tab = tab,
                color = tint,
                modifier = Modifier
                    .size(TabGlyphSize)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Text(
                tab.label,
                fontSize = 10.sp,
                color = tint,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.5.sp
            )
        }
        if (tab == AppTab.TRIP && tripRunning) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-2).dp)
                    .size(6.dp)
                    .clip(Capsule)
                    .background(palette.vermilion)
            )
        }
    }
}
