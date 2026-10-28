package com.blindwalk.app.ui.trip

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindwalk.app.TripStore
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.location.TripService
import com.blindwalk.app.ui.components.PillButton
import com.blindwalk.app.ui.components.StatusChip
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.NumericFamily
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.SerifFamily
import com.blindwalk.app.ui.components.BwDialog
import com.blindwalk.app.ui.theme.Vermilion
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.glass.liquidGlass
import com.blindwalk.app.ui.wallpaper.LocalWallpaperActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun TripScreen(
    onFinished: (String) -> Unit,
    onDiscarded: () -> Unit
) {
    val context = LocalContext.current
    val record by TripStore.record.collectAsStateWithLifecycle()
    val instruction by TripStore.instruction.collectAsStateWithLifecycle()
    val pendingDiscard by TripStore.pendingDiscard.collectAsStateWithLifecycle()
    val location by TripStore.location.collectAsStateWithLifecycle()

    // 用 State 而不是普通可变值：读它的人才会重组。
    // 之前把 now 当普通值读在 TripScreen 顶层，整页每秒重组 4 次，
    // 实际每帧只有计时那三个字符变了。
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showEndDialog by remember { mutableStateOf(false) }

    // 从首页「结束」进来：自动弹确认框
    LaunchedEffect(Unit) {
        if (TripStore.pendingEndDialog.value) {
            TripStore.pendingEndDialog.value = false
            showEndDialog = true
        }
    }

    // 只在运行时走表，暂停即冻结。
    LaunchedEffect(record?.status, record?.segmentStartedAt) {
        if (record?.status != SessionStatus.RUNNING) return@LaunchedEffect
        while (isActive) {
            nowState.longValue = System.currentTimeMillis()
            delay(250)
        }
    }

    LaunchedEffect(record?.status) {
        val current = record
        if (current != null && current.status == SessionStatus.ENDED) {
            onFinished(current.id)
            TripStore.clear()
        }
    }

    // 作废由服务完成（搬进暂存区），这里只负责离开行程页。
    // 必须确认"待作废的就是本段行程"：否则上一段的撤销窗口还没关时开始新行程，
    // 新行程会被旧的作废信号立刻弹回首页（实测踩到过）。
    var lastTripId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(record?.id) {
        val current = record
        if (current != null) lastTripId = current.id
    }
    LaunchedEffect(pendingDiscard, lastTripId) {
        val discarded = pendingDiscard ?: return@LaunchedEffect
        if (discarded == lastTripId) onDiscarded()
    }

    BackHandler { showEndDialog = true }

    val status = record?.status
    val canDraw = status == SessionStatus.RUNNING
    val hasWallpaper = LocalWallpaperActive.current

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "瞎走",
                fontFamily = SerifFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 4.sp,
                color = Ink
            )
            Spacer(Modifier.weight(1f))
            StatusChip(status)
        }

        Spacer(Modifier.height(22.dp))

        ElapsedText(record = record, nowState = nowState)

        Spacer(Modifier.height(16.dp))
        LocationLine(location, nowState)

        Spacer(Modifier.height(16.dp))

        // 指令区：按权重分剩余高度，不再写死高度——
        // 写死的话，短屏上印章会被挤到没有上下留白。
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = instruction,
                transitionSpec = {
                    // 垂直翻牌：新指令从下方顶上来，旧的往上走。
                    // 比单纯缩放更像"翻出一张牌"。
                    (slideInVertically(animationSpec = tween(260)) { it / 2 } + fadeIn(tween(200)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(200)) { -it / 2 } + fadeOut(tween(160))
                        )
                },
                label = "instruction"
            ) { event ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        event?.type?.arrow ?: "·",
                        fontSize = 46.sp,
                        color = Vermilion
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event?.type?.label ?: "抽一条",
                        fontFamily = SerifFamily,
                        // 字号按可用空间自动收缩：长指令在窄屏上不会溢出。
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 22.sp,
                            maxFontSize = if (event == null) 44.sp else 82.sp
                        ),
                        fontWeight = FontWeight.Normal,
                        color = if (event == null) Muted else Ink,
                        letterSpacing = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val current = record
            val (distValue, distUnit) = Fmt.distanceParts(current?.distanceMeters ?: 0.0)
            StatBlock("总路程", distValue, distUnit, Modifier.weight(1f))

            // 没有计步器或用户拒了权限时，这一列整项不出现——不显示假 0。
            if (current?.stepBaseline != null) {
                StatBlock("步数", Fmt.steps(current.steps), "步", Modifier.weight(1f))
            }

            StatBlock("指路次数", "${current?.instructionCount ?: 0}", "次", Modifier.weight(1f))
        }

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.94f else 1f,
            animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
            label = "press"
        )

        // 初始值 1 = 完全消散。抽签时归零再涨回 1，墨环才会"扩一次"。
        val inkRing = remember { Animatable(1f) }
        LaunchedEffect(instruction?.at) {
            if (instruction != null) {
                inkRing.snapTo(0f)
                inkRing.animateTo(1f, tween(620))
            }
        }

        // 指路按钮：整宽圆角矩形，高度跟屏幕高度挂钩。
        // 矩形比圆形好排 —— 宽度固定用满内容宽，高度按比例给，
        // 剩下的纵向空间全留给上面的指令区，短屏也不会互相挤。
        val sealHeight = (LocalConfiguration.current.screenHeightDp * 0.20f)
            .coerceIn(96f, 208f)
            .dp
        val sealShape = RoundedCornerShape(28.dp)

        BoxWithConstraints(
            Modifier
                .weight(1.4f)
                .fillMaxWidth()
                // 上下各保底 24dp：印章不会贴到统计行，也不会贴到下面的按钮
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // 屏幕矮的时候按比例算出来的高度会撑爆布局，把下面的按钮顶出屏幕，
            // 所以再按实际可用高度夹一次。
            val sealBoxHeight = minOf(sealHeight, maxHeight)
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // 盖章的"余墨"：一圈墨色细环从按钮边缘向外扩散并淡出（跟着改成圆角矩形）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(sealBoxHeight + (32 * (1f - inkRing.value)).dp)
                        .graphicsLayer {
                            val s = 1f + 0.04f * (1f - inkRing.value)
                            scaleX = s
                        }
                        .border(
                            width = (1f + 2.5f * (1f - inkRing.value)).dp,
                            color = BWTheme.colors.ink.copy(alpha = 0.40f * (1f - inkRing.value)),
                            shape = sealShape
                        )
                )

                val sealFill = when {
                    !canDraw -> Vermilion.copy(alpha = 0.22f)
                    // 铺了壁纸时按钮略微透出背景，但只减一点点：
                    // 这是主操作，再透就看不清了。
                    hasWallpaper -> Vermilion.copy(alpha = 0.86f)
                    else -> Vermilion
                }
                val sealGlass = LocalGlassEnabled.current

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(sealBoxHeight)
                        .scale(pressScale)
                        .clip(sealShape)
                        .then(
                            if (sealGlass) {
                                // 印章是整页最大的一块，玻璃用强一点的折射，才有"凸起"的感觉。
                                Modifier.liquidGlass(
                                    shape = sealShape,
                                    tint = sealFill,
                                    blurRadius = 16f,
                                    refractionHeight = 30f,
                                    refractionAmount = 60f
                                )
                            } else {
                                Modifier.background(sealFill)
                            }
                        )
                        .clickable(
                            enabled = canDraw,
                            interactionSource = interaction,
                            indication = null
                        ) {
                            TripService.send(context, TripService.ACTION_DRAW)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (canDraw) "指路" else "已暂停",
                        color = Paper,
                        fontFamily = SerifFamily,
                        // 字号跟着按钮高度走：矩形高度变了，字也跟着变，不会显得空或顶边。
                        fontSize = if (canDraw) (sealBoxHeight.value * 0.30f).sp else 20.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 8.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillButton(
                text = if (status == SessionStatus.PAUSED) "继续" else "暂停",
                onClick = { TripService.send(context, TripService.ACTION_TOGGLE_PAUSE) },
                modifier = Modifier.weight(1f),
                filled = false,
                bordered = false,
                enabled = status == SessionStatus.RUNNING || status == SessionStatus.PAUSED
            )
            PillButton(
                text = "结束",
                onClick = { showEndDialog = true },
                modifier = Modifier.weight(1f),
                filled = false,
                bordered = false,
                enabled = status == SessionStatus.RUNNING || status == SessionStatus.PAUSED
            )
        }

        // 底栏悬在内容之上：给它让出位置（74dp 底栏 + 12dp 呼吸）
        Spacer(Modifier.height(86.dp))
    }

    if (showEndDialog) {
        EndTripDialog(
            durationText = Fmt.durationFull(record?.activeMillis(nowState.longValue) ?: 0L),
            distanceText = Fmt.distance(record?.distanceMeters ?: 0.0),
            count = record?.instructionCount ?: 0,
            onDismiss = { showEndDialog = false },
            onSaveAndEnd = {
                showEndDialog = false
                TripService.send(context, TripService.ACTION_STOP)
            },
            onDiscard = {
                showEndDialog = false
                TripService.send(context, TripService.ACTION_DISCARD)
            }
        )
    }
}

/**
 * 结束/作废三选一。
 *
 * 布局刻意做成"上主下险"：保存并结束在最上且字色最深；
 * 作废被一条分隔线压在最底部、用灰色——两个意图相反的动作不能挨在一起。
 */
@Composable
private fun EndTripDialog(
    durationText: String,
    distanceText: String,
    count: Int,
    onDismiss: () -> Unit,
    onSaveAndEnd: () -> Unit,
    onDiscard: () -> Unit
) {
    BwDialog(onDismissRequest = onDismiss) {
        Text(
            "结束这次瞎走？",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Ink
        )
        Text(
            "已走 $durationText · $distanceText · $count 次指路",
            fontSize = 13.sp,
            color = Muted
        )

        Spacer(Modifier.height(20.dp))

        DialogOption(
            title = "保存并结束",
            subtitle = "生成总结卡片，存入历史",
            titleColor = Ink,
            subtitleColor = Muted,
            onClick = onSaveAndEnd
        )
        DialogOption(
            title = "继续走",
            subtitle = null,
            titleColor = Ink,
            subtitleColor = Muted,
            onClick = onDismiss
        )

        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Rule, thickness = 1.dp)
        Spacer(Modifier.height(6.dp))

        DialogOption(
            title = "作废这次",
            subtitle = "不生成卡片，不留记录（5 秒内可撤销）",
            titleColor = Muted,
            subtitleColor = Muted.copy(alpha = 0.75f),
            onClick = onDiscard
        )
    }
}

@Composable
private fun DialogOption(
    title: String,
    subtitle: String?,
    titleColor: androidx.compose.ui.graphics.Color,
    subtitleColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = subtitleColor)
        }
    }
}

/** 只让这一小块跟着计时器重组，而不是整页。 */
@Composable
private fun ElapsedText(record: SessionRecord?, nowState: State<Long>) {
    // 注意：record 是参数值，不是 State。若直接把它捕获进 remember 的 derivedStateOf，
    // 闭包会永远拿着首次组合时那份（此时 segmentStartedAt 还是 null），计时器就卡在 0。
    // rememberUpdatedState 让它每次重组都刷新，派生值才能跟着 record 变化。
    val current by rememberUpdatedState(record)

    // 计时器每 250ms 更新一次，但秒数每秒才变一次。
    // 用 derivedStateOf 把"毫秒变化"折叠成"秒变化"，Text 就只重组 1/4 的次数。
    val text by remember {
        derivedStateOf {
            Fmt.durationFull(current?.activeMillis(nowState.value) ?: 0L)
        }
    }
    Text(
        text,
        fontSize = 54.sp,
        fontFamily = FontFamily.Monospace,
        color = Ink,
        letterSpacing = 2.sp
    )
}

/**
 * 定位状态行。
 * 没有这一行，用户只能盯着「0 m」猜：是没开始走，还是根本没搜到星。
 */
@Composable
private fun LocationLine(state: TripStore.LocationState, nowState: State<Long>) {
    val now = nowState.value
    val (text, color) = when {
        state.locating -> {
            val acc = state.accuracyMeters
            val suffix = if (acc != null) "（当前信号 ±${acc.roundToInt()}m，等 GPS）" else ""
            "正在定位…$suffix" to Muted
        }
        state.lastFixAt == null -> "无信号" to Vermilion
        else -> {
            val ageSec = ((now - state.lastFixAt) / 1000).coerceAtLeast(0L)
            val acc = state.accuracyMeters ?: 0f
            when {
                acc > 30f -> "信号弱 ±${acc.roundToInt()}m，暂不记录" to Vermilion
                ageSec > 15L -> "信号丢失 ${ageSec}s" to Vermilion
                else -> "已定位 ±${acc.roundToInt()}m · ${ageSec}s 前 · ${state.acceptedPoints} 点" to Muted
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = color, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun StatBlock(
    label: String,
    value: String,
    unit: String = "",
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        // 三列并排后每列只有 1/3 宽，数字一长（尤其是 12,345 步）就会撑破。
        // 这里量一次文字宽度，从 26sp 往下收，直到放得下为止——三项共用同一套逻辑，
        // 所以同一个屏幕上三个数字的字号是一致的，不会一大一小。
        val available = constraints.maxWidth.toFloat()
        var size = 26.sp
        while (size > 13.sp) {
            val width = measurer.measure(
                text = value,
                style = TextStyle(
                    fontSize = size,
                    fontFamily = NumericFamily,
                    fontWeight = FontWeight.Medium
                ),
                softWrap = false,
                maxLines = 1
            ).size.width.toFloat()
            if (width <= available) break
            size = (size.value - 1f).sp
        }

        Column {
            Text(label, fontSize = 11.sp, color = Muted, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // 数字滚动：值变化时从下方推上来，而不是硬跳。
                AnimatedContent(
                    targetState = value,
                    transitionSpec = {
                        (slideInVertically(tween(220)) { it } + fadeIn(tween(180)))
                            .togetherWith(slideOutVertically(tween(180)) { -it } + fadeOut(tween(140)))
                    },
                    label = "stat"
                ) { v ->
                    Text(
                        v,
                        fontSize = size,
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                        fontFamily = NumericFamily,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, fontSize = 11.sp, color = Muted)
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
    }
}
