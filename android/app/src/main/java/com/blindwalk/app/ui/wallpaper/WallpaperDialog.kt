package com.blindwalk.app.ui.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.data.ThemeMode
import com.blindwalk.app.data.WallpaperState
import com.blindwalk.app.ui.components.BwDialog
import com.blindwalk.app.ui.components.BwDialogTitle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.blindwalk.app.ui.glass.fractionOf
import com.blindwalk.app.ui.glass.BwSlider
import com.blindwalk.app.ui.glass.BwSwitch
import com.blindwalk.app.ui.glass.liquidGlassChip
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.SerifFamily
import com.blindwalk.app.ui.theme.Vermilion
import kotlin.math.roundToInt

/**
 * 「窗纸」二级弹窗：换图、调遮罩、定主题。
 *
 * 三个控件的关系是：壁纸决定"看什么"，遮罩决定"看得清不清"，
 * 主题决定"文字用什么色"。所以顺序也按这个逻辑排。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PaperScreen(
    state: WallpaperState,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onRemove: () -> Unit,
    onVeil: (Int) -> Unit,
    onBlur: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onGlass: (Boolean) -> Unit,
    onAccentFromWallpaper: (Boolean) -> Unit,
    onOpenInfo: () -> Unit
) {
    // 滑块要跟手，所以本地存一份，松手时才落到存储里。
    // 注意不要用 remember(state.veil) 直接做键：松手写回存储会改变这个键，
    // 从而重建一个全新的状态对象；而手势协程捕获的还是旧对象，
    // 结果第二次拖动时值写进了一个已经没人读的 state（表现就是"拖动不动、松手才跳"）。
    var veil by remember { mutableFloatStateOf(state.veil.toFloat()) }
    var blur by remember { mutableFloatStateOf(state.blur.toFloat()) }
    LaunchedEffect(state.veil) { veil = state.veil.toFloat() }
    LaunchedEffect(state.blur) { blur = state.blur.toFloat() }

    // 纸色：拖动过程中是连续位置（跟手），松手才吸附到最近一档。
    var themePos by remember { mutableFloatStateOf(state.themeMode.ordinal.toFloat()) }
    LaunchedEffect(state.themeMode) { themePos = state.themeMode.ordinal.toFloat() }

    // 玻璃关掉时退回原来的 Material 控件，保证两种模式都好看。
    val glassOn = LocalGlassEnabled.current

    // 从弹窗改成一整页：底栏可以直达，弹窗那个"卡片里再滚动"的壳子就不需要了。
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
            .padding(bottom = 86.dp)
                .padding(horizontal = 24.dp)
        ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "窗纸",
                    fontFamily = com.blindwalk.app.ui.theme.SerifFamily,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 6.sp,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "换一层纸。图按屏幕比例裁好，只存在本机。",
                    fontSize = 13.sp,
                    color = Muted,
                    letterSpacing = 0.5.sp,
                    lineHeight = 20.sp
                )
            }
            com.blindwalk.app.ui.info.ShushuButton(onClick = onOpenInfo)
        }

        Spacer(Modifier.height(14.dp))

        val isDarkPaper = Ink.luminance() < 0.5f

        // 预览
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1D1B))
        ) {
            val bitmap = rememberWallpaperBitmap(state.path, state.revision)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        // 和真实壁纸层用同一套增益（veilGain + brightnessFilter）：
                        .graphicsLayer {
                            colorFilter = brightnessFilter(veilGain(veil.roundToInt(), isDarkPaper))
                        }
                        .then(
                            if (blur > 0) {
                                Modifier.blur(blur.dp, BlurredEdgeTreatment.Rectangle)
                            } else Modifier
                        ),
                    contentScale = ContentScale.Crop
                )
                
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有选图", fontSize = 13.sp, color = Color(0xFF9C958B))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogChip(
                text = if (state.hasWallpaper) "更换图片" else "选择图片",
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = onPickImage
            )
            if (state.hasWallpaper) {
                DialogChip(
                    text = "移除",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = onRemove
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Divider()

        // ---------------------------------------------------------- 遮罩
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("遮罩", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    veil < -1f -> "加暗 ${(-veil).roundToInt()}%"
                    veil > 1f -> "加亮 ${veil.roundToInt()}%"
                    else -> "原样"
                },
                fontSize = 14.sp,
                color = Muted
            )
        }
        if (glassOn) {
            BwSlider(
                value = veil,
                onValueChange = { veil = it },
                onValueChangeFinished = { onVeil(veil.roundToInt()) },
                valueRange = -80f..80f,
                // 双向滑块：填充从中点（原样）往两边长。
                bipolar = true
            )
        } else {
            Slider(
                value = veil,
                onValueChange = { veil = it },
                onValueChangeFinished = { onVeil(veil.roundToInt()) },
                valueRange = -80f..80f,
                colors = SliderDefaults.colors(thumbColor = Vermilion),
                thumb = { PaperThumb() },
                // 自定义轨道：双向滑块要从中点往两边填，
                // 顺便去掉 M3 1.4 那个轨道端点小圆点（colors 里没有开关）。
                track = { state ->
                    PaperSliderTrack(sliderState = state, bipolar = true)
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("加暗", fontSize = 13.sp, color = Muted)
            // 隐藏交互区：拖回正中很难精确，点一下「原样」就归零。
            // 用无涟漪的 clickable + 内边距，视觉上只是三个字的标签，
            // 实际可点范围比字形大一圈。
            Text(
                "原样",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        veil = 0f
                        onVeil(0)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Text("加亮", fontSize = 13.sp, color = Muted)
        }

        Spacer(Modifier.height(16.dp))
        Divider()

        // ---------------------------------------------------------- 模糊
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("模糊", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                if (blur < 2f) "清晰" else "${blur.roundToInt()} dp",
                fontSize = 14.sp,
                color = Muted
            )
        }
        if (glassOn) {
            BwSlider(
                value = blur,
                onValueChange = { blur = it },
                // 拖到最左端常常停在 1~2dp 上，肉眼跟 0 没区别，但会显示成"1 dp"，
                // 看起来像没关干净。这里做一次吸附。
                onValueChangeFinished = { onBlur(if (blur < 2f) 0 else blur.roundToInt()) },
                valueRange = 0f..40f
            )
        } else {
            Slider(
                value = blur,
                onValueChange = { blur = it },
                onValueChangeFinished = { onBlur(if (blur < 2f) 0 else blur.roundToInt()) },
                valueRange = 0f..40f,
                colors = SliderDefaults.colors(thumbColor = Vermilion),
                thumb = { PaperThumb() },
                track = { state ->
                    PaperSliderTrack(sliderState = state, bipolar = false)
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("清晰", fontSize = 13.sp, color = Muted)
            Text("模糊", fontSize = 13.sp, color = Muted)
        }

        Spacer(Modifier.height(16.dp))
        Divider()

        // ---------------------------------------------------------- 主题
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("纸色", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            Text(
                // 拖动时显示"最近的那一档"，松手即生效，不用等吸附完成。
                ThemeMode.entries[themePos.roundToInt().coerceIn(0, 2)].label,
                fontSize = 14.sp,
                color = Muted
            )
        }
        if (glassOn) {
            // 拖动时拇指跟手，松手吸附到最近一档——不是拖到哪就直接跳过去。
            BwSlider(
                value = themePos,
                onValueChange = { themePos = it },
                onValueChangeFinished = {
                    val snapped = themePos.roundToInt().coerceIn(0, 2)
                    // 显式拨回整数档：只靠 state.themeMode 回环的话，
                    // 吸附到「当前档」时值没变，回环不触发，拇指会停在两档之间。
                    themePos = snapped.toFloat()
                    onTheme(ThemeMode.entries[snapped])
                },
                valueRange = 0f..2f
            )
        } else {
            Slider(
                value = themePos,
                onValueChange = { themePos = it },
                onValueChangeFinished = {
                    val snapped = themePos.roundToInt().coerceIn(0, 2)
                    // 显式拨回整数档：只靠 state.themeMode 回环的话，
                    // 吸附到「当前档」时值没变，回环不触发，拇指会停在两档之间。
                    themePos = snapped.toFloat()
                    onTheme(ThemeMode.entries[snapped])
                },
                valueRange = 0f..2f,
                steps = 1,
                colors = SliderDefaults.colors(thumbColor = Vermilion),
                thumb = { PaperThumb() },
                track = { state ->
                    PaperSliderTrack(sliderState = state, bipolar = false)
                }
            )
        }
        // 三等分，而不是 SpaceBetween：
        // 「跟随系统」比「墨纸」宽，SpaceBetween 会把中间项的中心推向右，
        // 而滑块三档的中点正好在控件正中，两者对不齐。
        Row(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val nearest = themePos.roundToInt().coerceIn(0, 2) == mode.ordinal
                Text(
                    mode.label,
                    modifier = Modifier.weight(1f),
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        ThemeMode.entries.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    fontSize = 13.sp,
                    color = if (nearest) Ink else Muted,
                    fontWeight = if (nearest) FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Divider()

        // ---------------------------------------------------- 主题色跟随壁纸
        Spacer(Modifier.height(12.dp))
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("主题色跟随壁纸", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (glassOn) {
                BwSwitch(
                    checked = state.accentFromWallpaper,
                    onCheckedChange = onAccentFromWallpaper
                )
            } else {
                Switch(
                    checked = state.accentFromWallpaper,
                    onCheckedChange = onAccentFromWallpaper,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = com.blindwalk.app.ui.theme.BWTheme.colors.paper,
                        checkedTrackColor = Vermilion,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Rule.copy(alpha = 0.6f),
                        uncheckedBorderColor = Rule
                    )
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            if (state.hasWallpaper) "从壁纸里取一个主色，替换朱砂。" else "先选一张图才能取色，现在用朱砂。",
            fontSize = 13.sp,
            color = Muted,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(27.dp))
        Divider()

        // ---------------------------------------------------- 液态玻璃（实验性）
        Spacer(Modifier.height(12.dp))
        // 固定 48dp 行高：Material 开关自带 48dp 触控高度，自绘开关只有 28dp。
        // 不固定的话，两种模式下这一行的高度差 20dp，下面的说明文字就会跟着上下跳。
        Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("液态玻璃", fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Vermilion.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("高性能选项", fontSize = 11.sp, color = Vermilion, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.weight(1f))
            if (glassOn) {
                BwSwitch(
                    checked = state.glass,
                    onCheckedChange = onGlass
                )
            } else {
                Switch(
                    checked = state.glass,
                    onCheckedChange = onGlass,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = com.blindwalk.app.ui.theme.BWTheme.colors.paper,
                        checkedTrackColor = Vermilion,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Rule.copy(alpha = 0.6f),
                        uncheckedBorderColor = Rule
                    )
                )
            }
        }
        // 「此处」带下划线和主题色，点了直接跳作者仓库
        val glassHint = buildAnnotatedString {
            append("液态玻璃效果来自 Kyant0/AndroidLiquidGlass，低端机上可能有较差的动画表现，点击")
            withLink(
                LinkAnnotation.Url(
                    url = "https://github.com/Kyant0/AndroidLiquidGlass",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Vermilion,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) { append("此处") }
            append("前往作者仓库")
        }
        Text(
            glassHint,
            fontSize = 13.sp,
            color = Muted,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Material 模式滑块的尺寸。三条滑块共用同一套，比 M3 默认（4dp 轨道 / 4dp 拇指）粗一档。
 */
private val PaperTrackHeight = 7.dp
private val PaperThumbWidth = 7.dp
private val PaperThumbHeight = 34.dp

/**
 * Material 模式的滑块轨道（全部自绘）。
 *
 * 一开始是想用 M3 自己的 Track 打底、只把填充叠上去，
 * 但 M3 1.4 会在轨道末端画一个小圆点，而 `SliderDefaults.colors` 里没有开关，
 * 三条轨道没法统一。索性全部自己画：尺寸一致、没有小点、双向滑块能从中点起填。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PaperSliderTrack(
    sliderState: androidx.compose.material3.SliderState,
    bipolar: Boolean
) {
    // 主题色是 @Composable 取值器，必须先取好再进绘制 lambda。
    val trackColor = Rule.copy(alpha = 0.55f)
    val accentColor = Vermilion
    val fraction = fractionOf(sliderState.value, sliderState.valueRange)

    Box {
        // 底：自绘轨道。三条滑块统一用这一条，尺寸完全一致。
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PaperTrackHeight)
        ) {
            val inset = PaperThumbWidth.toPx() / 2f
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(inset, 0f),
                size = Size((size.width - inset * 2f).coerceAtLeast(0f), size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
        }

        // 上：从中点（或左端）到拇指的填充。
        Canvas(Modifier.matchParentSize()) {
            val halfThumb = PaperThumbWidth.toPx() / 2f
            val span = (size.width - halfThumb * 2f).coerceAtLeast(1f)
            val tx = halfThumb + span * fraction.coerceIn(0f, 1f)
            val r = size.height / 2f

            if (bipolar) {
                val mid = size.width / 2f
                val left = minOf(mid, tx)
                val right = maxOf(mid, tx)
                if (right - left > 0.5f) {
                    // 中点那一端是平头：圆头会在轨道中间鼓出一个"棒棒糖"，
                    // 和轨道接不上。靠拇指那端保持圆头，正好接上轨道断口。
                    drawFillRect(
                        color = accentColor,
                        left = left,
                        right = right,
                        height = size.height,
                        radius = r,
                        roundLeft = right == tx && mid < tx,
                        roundRight = left == tx && mid > tx
                    )
                }
            } else if (tx > halfThumb + 0.5f) {
                // 单极：左端就是轨道起点，保持圆头才和轨道端帽一致
                drawFillRect(
                    color = accentColor,
                    left = halfThumb,
                    right = tx,
                    height = size.height,
                    radius = r,
                    roundLeft = true,
                    roundRight = true
                )
            }
        }
    }
}

/** 自绘拇指：比 M3 默认的 4dp 竖条粗一档，也短一点。 */
@Composable
private fun PaperThumb() {
    val color = Vermilion
    Box(
        Modifier
            .width(PaperThumbWidth)
            .height(PaperThumbHeight)
            .clip(RoundedCornerShape(PaperThumbWidth / 2))
            .background(color)
    )
}
/** 画一段可以分别控制左右端是否圆头的填充条。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFillRect(
    color: Color,
    left: Float,
    right: Float,
    height: Float,
    radius: Float,
    roundLeft: Boolean,
    roundRight: Boolean
) {
    val l = if (roundLeft) radius else 0f
    val rr = if (roundRight) radius else 0f
    val path = Path().apply {
        addRoundRect(
            androidx.compose.ui.geometry.RoundRect(
                left = left,
                top = 0f,
                right = right,
                bottom = height,
                topLeftCornerRadius = CornerRadius(l),
                topRightCornerRadius = CornerRadius(rr),
                bottomRightCornerRadius = CornerRadius(rr),
                bottomLeftCornerRadius = CornerRadius(l)
            )
        )
    }
    drawPath(path, color)
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Rule.copy(alpha = 0.7f))
    )
}

@Composable
private fun DialogChip(
    text: String,
    primary: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val glass = LocalGlassEnabled.current
    val shape = RoundedCornerShape(21.dp)
    // 主按钮是墨色实底、次按钮是极淡的描边底。
    val fill = if (primary) Ink.copy(alpha = com.blindwalk.app.ui.glass.GLASS_TINT_ALPHA) else Rule.copy(alpha = 0.35f)

    Box(
        modifier
            .height(42.dp)
            .clip(shape)
            .then(
                if (glass) {
                    // 和「窗纸」卡片、底栏用同一套玻璃：按下有柔光和环形扭曲
                    Modifier.liquidGlassChip(shape = shape, tint = fill)
                } else {
                    Modifier.background(fill)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.sp,
            color = if (primary) com.blindwalk.app.ui.theme.BWTheme.colors.paper else Ink,
            textAlign = TextAlign.Center
        )
    }
}
