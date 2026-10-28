package com.blindwalk.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.TripStore
import com.blindwalk.app.location.TripService
import com.blindwalk.app.domain.history.HistorySection
import com.blindwalk.app.domain.history.SectionKind
import com.blindwalk.app.domain.history.SortKey
import com.blindwalk.app.domain.history.SortSpec
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.ui.components.BwDialog
import com.blindwalk.app.ui.components.BwDialogActions
import com.blindwalk.app.ui.components.BwDialogTitle
import com.blindwalk.app.ui.components.BwSnackbarHost
import com.blindwalk.app.ui.components.BwSnackbarState
import com.blindwalk.app.ui.components.PillButton
import com.blindwalk.app.ui.glass.GLASS_TINT_ALPHA
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.glass.liquidGlass
import com.blindwalk.app.ui.glass.liquidGlassRim
import com.blindwalk.app.ui.glass.liquidGlassChip
import com.blindwalk.app.ui.wallpaper.ImageCropperDialog
import com.blindwalk.app.ui.wallpaper.LocalWallpaperAspect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.blindwalk.app.ui.components.StatusChip
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.NumericFamily
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.SerifFamily
import com.blindwalk.app.ui.theme.Vermilion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFS = "blindwalk"
private const val KEY_SWIPE_HINT = "swipe_hint_shown"

/**
 * 行卡片自身的形状：直角。
 *
 * 不能用 RectangleShape——它不是 CornerBasedShape，库的 lens 折射会抛
 * UnsupportedOperationException。半径 0 的 RoundedCornerShape 才是合法的。
 *
 * 圆角由外层容器的 clip 提供：闭合时四条边都贴着容器，看起来是圆角；
 * 滑开后接缝那一侧缩进了容器内部，自然就是直角，正好贴合直角的动作按钮。
 */
private val SquareRowShape = RoundedCornerShape(0.dp)

/** 左右滑卡片的圆角。动作层的裁剪形状必须用同一个值，否则又会出现混色缝。 */
private val SWIPE_CORNER = 18.dp

/** 滑动露出的方向。 */
private enum class SwipeSide { PIN, ACTIONS }

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onResumeDiscarded: () -> Unit,
    /** 点开进行中/已暂停的记录：回行程页继续 */
    onResumeTrip: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val trashCount by vm.trashCount.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val selectionMode by vm.selectionMode.collectAsStateWithLifecycle()
    val pendingDiscard by TripStore.pendingDiscard.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val snackbar = remember { BwSnackbarState() }
    val scope = rememberCoroutineScope()

    var hintDismissed by remember { mutableStateOf(prefs.getBoolean(KEY_SWIPE_HINT, false)) }
    var openRowId by remember { mutableStateOf<String?>(null) }
    var openSide by remember { mutableStateOf<SwipeSide?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    // 回收站里还原/彻底删除后回到首页：列表是缓存住的，靠版本号触发重扫
    val historyVersion by com.blindwalk.app.data.HistoryEvents.version.collectAsStateWithLifecycle()
    LaunchedEffect(historyVersion) {
        if (historyVersion > 0) vm.refresh()
    }

    // 刚从行程页作废回来：给 4 秒撤销窗口。
    LaunchedEffect(pendingDiscard) {
        val id = pendingDiscard ?: return@LaunchedEffect
        snackbar.show(
            scope = scope,
            text = "已作废这次行程",
            action = "撤销",
            onAction = {
                TripStore.pendingDiscard.value = null
                vm.undoDiscard(id) { onResumeDiscarded() }
            },
            onTimeout = {
                TripStore.pendingDiscard.value = null
                vm.purgeDiscard(id)
            }
        )
    }

    fun dismissHint() {
        if (!hintDismissed) {
            hintDismissed = true
            prefs.edit().putBoolean(KEY_SWIPE_HINT, true).apply()
        }
    }

    fun closeRow() {
        openRowId = null
        openSide = null
    }

    fun deleteOne(record: SessionRecord) {
        closeRow()
        vm.deleteToTrash(record.id)
        snackbar.show(
            scope = scope,
            text = "已移入回收站",
            action = "撤销",
            onAction = { vm.undoDelete(record.id) }
        )
    }

    val showHint = !hintDismissed && sections.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            if (selectionMode) {
                SelectionBar(
                    count = selection.size,
                    onSelectAll = { vm.selectAll() },
                    onCancel = { vm.clearSelection() }
                )
            } else {
                HomeHeader(
                    sort = sort,
                    onSortChange = { vm.setSort(it) },
                    onTrash = onOpenTrash,
                    trashCount = trashCount,
                    statsText = if (stats.count > 0) {
                        "共 ${stats.count} 次 · ${Fmt.distance(stats.distanceMeters)} · ${Fmt.duration(stats.activeMillis)}"
                    } else null
                )
            }

            if (showHint) {
                Spacer(Modifier.height(12.dp))
                SwipeHint(onDismiss = { dismissHint() })
            }

            Spacer(Modifier.height(16.dp))

            if (sections.isEmpty()) {
                // 必须 fillMaxWidth：Column 里 weight 只分配高度，
                // 不加宽度限制时 Box 会缩成文字那么宽并贴左边，contentAlignment 等于失效。
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "还没有走过\n去「瞎走」把方向盘交给随机",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 26.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sections.forEach { section ->
                        item(key = section.key) { SectionHeader(section) }
                        items(section.items, key = { it.id }) { record ->
                            SwipeRow(
                                record = record,
                                selectionMode = selectionMode,
                                selected = record.id in selection,
                                openSide = if (openRowId == record.id) openSide else null,
                                onOpen = { side ->
                                    dismissHint()
                                    openRowId = record.id
                                    openSide = side
                                },
                                onClose = { if (openRowId == record.id) closeRow() },
                                onTap = {
                                    when {
                                        openRowId != null -> closeRow()
                                        selectionMode -> vm.toggleSelection(record.id)
                                        record.status == SessionStatus.RUNNING ||
                                            record.status == SessionStatus.PAUSED -> onResumeTrip()
                                        else -> onOpen(record.id)
                                    }
                                },
                                onLongPress = {
                                    if (!selectionMode) vm.enterSelection(record.id)
                                },
                                onPin = {
                                    closeRow()
                                    vm.setPinned(record.id, !record.pinned)
                                },
                                onSelect = {
                                    closeRow()
                                    vm.enterSelection(record.id)
                                },
                                onDelete = { deleteOne(record) },
                                onTogglePause = {
                                    closeRow()
                                    TripService.send(context, TripService.ACTION_TOGGLE_PAUSE)
                                    vm.refreshSoon()
                                },
                                onEndTrip = {
                                    closeRow()
                                    // 交给行程页：切过去后自动弹「结束这次瞎走？」
                                    TripStore.pendingEndDialog.value = true
                                    onResumeTrip()
                                }
                            )
                        }
                    }
                    // 底栏悬在内容之上，列表尾部给它让出位置（66 + 页面底部 20 = 86，正好越过底栏）
                    item(key = "tail") { Spacer(Modifier.height(66.dp)) }
                }
            }

            if (selectionMode) {
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = if (selection.isEmpty()) "请选择" else "移入回收站（${selection.size}）",
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    filled = true,
                    enabled = selection.isNotEmpty()
                )

                    // 底栏悬在内容之上：按钮要浮到它上面（66 + 页面底部 20 = 86）
                    Spacer(Modifier.height(66.dp))
            }

            Spacer(Modifier.height(20.dp))
        }

        BwSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 86.dp)
        )
    }


    if (showDeleteConfirm) {
        BwDialog(onDismissRequest = { showDeleteConfirm = false }) {
            BwDialogTitle("移入回收站？")
            Spacer(Modifier.height(10.dp))
            Text(
                "将移入回收站 ${selection.size} 条记录，不会真正删除，随时可以恢复。",
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            BwDialogActions(
                confirmLabel = "移入回收站",
                onCancel = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    vm.moveSelectionToTrash()
                }
            )
        }
    }

}

/**
 * 解码相册图片。
 *
 * 先只读尺寸再按 2 的幂降采样，避免一张 4000×9000 的照片直接解码吃掉 140MB。
 * 上限 3072 而不是 2048：裁剪是"按屏幕比例取中间一块"，
 * 竖屏图裁完只剩整高的那一截，2048 的上限会让壁纸只有 540 宽，铺到 1080 屏上等于 2 倍拉伸。
 */
private fun decodeForCrop(context: Context, uri: Uri, maxDim: Int = 3072): android.graphics.Bitmap? {
    return runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2

        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}

// ---------------------------------------------------------------- 滑动行

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRow(
    record: SessionRecord,
    selectionMode: Boolean,
    selected: Boolean,
    openSide: SwipeSide?,
    onOpen: (SwipeSide) -> Unit,
    onClose: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPin: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    /** 进行中的记录：暂停/继续、结束 */
    onTogglePause: () -> Unit,
    onEndTrip: () -> Unit
    
) {
    val density = LocalDensity.current
    val pinWidthPx = with(density) { 92.dp.toPx() }
    val actionsWidthPx = with(density) { 160.dp.toPx() }

    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val revealShape = remember { ActionsRevealShape({ offset.value }, SWIPE_CORNER) }

    // 外部决定开合（比如另一行被打开时，这一行要自动收回）。
    LaunchedEffect(openSide, selectionMode) {
        val target = when {
            selectionMode -> 0f
            openSide == SwipeSide.PIN -> pinWidthPx
            openSide == SwipeSide.ACTIONS -> -actionsWidthPx
            else -> 0f
        }
        if (abs(offset.value - target) > 0.5f) {
            offset.animateTo(target, tween(180))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SWIPE_CORNER))
    ) {
        // 背景动作区：左滑露右侧两个，右滑露左侧一个。
        //
        // 裁剪形状 = 容器圆角矩形 − 卡片圆角矩形：
        // 卡片压住的那一块根本不绘制按钮，所以卡片圆角那圈抗锯齿像素底下没有红/黑可混，
        // 只会和页面底色混，看起来就是一条干净的边。
        // （之前只做"收起时隐藏"，滑开后卡片和按钮贴着，混色依旧。）
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    shape = revealShape
                }
        ) {
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(160.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (record.status == SessionStatus.RUNNING || record.status == SessionStatus.PAUSED) {
                    // 进行中的记录：只给暂停/继续和结束，避免误删正在记录的数据
                    ActionBlock(
                        if (record.status == SessionStatus.PAUSED) "继续" else "暂停",
                        BWTheme.colors.ink,
                        Modifier.weight(1f),
                        onTogglePause
                    )
                    ActionBlock("结束", BWTheme.colors.vermilion, Modifier.weight(1f), onEndTrip)
                } else {
                    ActionBlock("多选", BWTheme.colors.actionNeutral, Modifier.weight(1f), onSelect)
                    ActionBlock("删除", BWTheme.colors.vermilion, Modifier.weight(1f), onDelete)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(92.dp)
            ) {
                ActionBlock(
                    text = if (record.pinned) "取消置顶" else "置顶",
                    color = BWTheme.colors.ink,
                    modifier = Modifier.fillMaxSize(),
                    onClick = onPin
                )
            }
        }

        // 前景内容
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !selectionMode,
                    state = rememberDraggableState { delta ->
                        val next = (offset.value + delta).coerceIn(-actionsWidthPx, pinWidthPx)
                        scope.launch { offset.snapTo(next) }
                    },
                    onDragStopped = { velocity ->
                        val current = offset.value
                        val side = when {
                            // 甩得够快也判定为打开，避免长距离拖动才有反馈。
                            velocity > 1200f -> SwipeSide.PIN
                            velocity < -1200f -> SwipeSide.ACTIONS
                            current > pinWidthPx * 0.5f -> SwipeSide.PIN
                            current < -actionsWidthPx * 0.5f -> SwipeSide.ACTIONS
                            else -> null
                        }
                        if (side != null) onOpen(side) else onClose()
                        val target = when (side) {
                            SwipeSide.PIN -> pinWidthPx
                            SwipeSide.ACTIONS -> -actionsWidthPx
                            null -> 0f
                        }
                        offset.animateTo(target, tween(180))
                    }
                )
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                )
        ) {
            // 卡片自己用矩形，不在这里做圆角：
            // 外侧圆角由外层容器的 clip(RoundedCornerShape) 提供，
            // 而滑开后接缝那一侧的边在容器内部，自然就是直角，正好贴合直角的动作按钮。
            //
            // 之前是用「随滑动距离收角」的 RoundedCornerShape，但那要靠组合阶段读取偏移量，
            // 依赖每帧重组，实机上出现过不收角的情况。改成静态矩形后不再依赖任何状态。
            RowContent(record, selectionMode, selected, SquareRowShape) { offset.value }
        }
    }
}

@Composable
private fun ActionBlock(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxHeight()
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = BWTheme.colors.paper,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
    }
}

/**
 * 动作层的裁剪形状 = 容器圆角矩形 − 卡片圆角矩形。
 *
 * 为什么需要它：卡片是圆角的，圆角边缘是抗锯齿的半透明像素。
 * 如果按钮就铺在卡片底下，这些像素会跟按钮颜色混合——纸色叠删除红就成了一道粉红弧线，
 * 叠置顶黑就成了一道脏灰弧线。把卡片覆盖的区域从按钮层里剪掉，底下只剩页面底色，
 * 混色也就无从发生。
 *
 * 卡片偏移在绘制阶段读取，滑动的每一帧都会重算，不需要重组。
 */
private class ActionsRevealShape(
    private val cardDx: () -> Float,
    private val radius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { CornerRadius(radius.toPx()) }
        val full = RoundRect(0f, 0f, size.width, size.height, r)
        val dx = cardDx()
        val card = RoundRect(dx, 0f, dx + size.width, size.height, r)

        val outer = Path().apply { addRoundRect(full) }
        val inner = Path().apply { addRoundRect(card) }
        val visible = Path().apply { op(outer, inner, PathOperation.Difference) }
        return Outline.Generic(visible)
    }
}

@Composable
private fun RowContent(
    record: SessionRecord,
    selectionMode: Boolean,
    selected: Boolean,
    cardShape: Shape,
    cardDx: () -> Float
) {
    // 列表里那条「进行中」的记录，磁盘上的 meta 可能还没有 segmentStartedAt，
    // 时长要取内存里的实时记录，否则永远是 0、也不会走字。
    val liveRecord = TripStore.record.collectAsState().value
    val shown = if (liveRecord != null && liveRecord.id == record.id) liveRecord else record

    // 进行中：每秒推一次 now，时长实时走字；已结束的不跑循环。
    val tickingNow by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = shown.status
    ) {
        if (shown.status == SessionStatus.RUNNING) {
            while (true) {
                delay(1000L)
                value = System.currentTimeMillis()
            }
        }
    }

    val glass = LocalGlassEnabled.current
    // 行卡片与「窗纸」按钮同一透明度：铺壁纸时能透出背景，列表仍然一格格看得清。
    val fill = (if (selected) BWTheme.colors.surfaceSelected else BWTheme.colors.surface)
        .copy(alpha = GLASS_TINT_ALPHA)

    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (glass) {
                    Modifier.liquidGlass(
                        shape = cardShape,
                        tint = fill,
                        blurRadius = 12f,
                        refractionHeight = 18f,
                        refractionAmount = 34f,
                        // 高光改由下面那层自绘：库内高光和卡片共用 shape，
                        // 卡片为了贴合直角按钮而变成矩形后，高光也会跟着变方。
                        highlight = null
                    )
                } else {
                    Modifier
                        .clip(cardShape)
                        .background(fill)
                }
            )
            .then(
                if (glass) {
                    Modifier.liquidGlassRim(cornerRadius = SWIPE_CORNER, dx = cardDx)
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            SelectionMark(selected)
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            val custom = record.displayTitleOrNull()
            Text(
                custom ?: "${Fmt.date(record.startedAt)} ${Fmt.weekday(record.startedAt)}",
                fontSize = 15.sp,
                color = Ink,
                fontWeight = if (custom != null) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.height(6.dp))

            val parts = buildList {
                if (custom != null) add(Fmt.date(record.startedAt))
                add(Fmt.distance(record.distanceMeters))
                add("${record.instructionCount} 次")
                if (record.pinned) add("置顶")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(parts.joinToString(" · "), fontSize = 12.sp, color = Muted)
                Spacer(Modifier.width(8.dp))
                StatusChip(record.status)
            }
        }

        Text(
            Fmt.duration(shown.activeMillis(shown.endedAt ?: tickingNow)),
            fontSize = 22.sp,
            color = Ink,
            fontWeight = FontWeight.Medium,
            fontFamily = NumericFamily
        )
    }
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) Ink else Color.Transparent)
            .border(1.5.dp, if (selected) Ink else Rule, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Text("✓", fontSize = 11.sp, color = Paper)
    }
}

@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BWTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "右滑置顶 · 左滑多选或删除",
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.weight(1f)
        )
        Text(
            "知道了",
            fontSize = 12.sp,
            color = Vermilion,
            modifier = Modifier.clickable(onClick = onDismiss)
        )
    }
}

// ---------------------------------------------------------------- 顶部区域

@Composable
private fun HomeHeader(
    sort: SortSpec,
    onSortChange: (SortSpec) -> Unit,
    onTrash: () -> Unit,
    trashCount: Int,
    statsText: String?
) {
    // 排序不再走弹窗：点表头那颗 chip 就地展开一排选项，选完自动收起
    var sortExpanded by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "瞎走",
                    fontFamily = SerifFamily,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 6.sp,
                    color = Ink
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "来一场放空大脑的漂流吧 ~",
                    fontSize = 13.sp,
                    color = Muted,
                    // 字距收到 0.5sp：360dp 宽的机器上，标题列被「窗纸」卡片挤到约 200dp，
                    // 1sp 的字距会让最后那个「~」掉到第二行。
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                if (statsText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(statsText, fontSize = 12.sp, color = Muted, letterSpacing = 0.5.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    HeaderAction(
                        text = "排序 ${sort.label}",
                        onClick = { sortExpanded = !sortExpanded },
                        active = sortExpanded
                    )
                    Spacer(Modifier.width(6.dp))
                    HeaderAction(
                        text = if (trashCount > 0) "回收站 $trashCount" else "回收站",
                        onClick = onTrash
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortKey.entries.forEach { key ->
                        SortChoiceChip(
                            label = key.label,
                            selected = key == sort.key,
                            onClick = { onSortChange(sort.copy(key = key)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SortChoiceChip(
                        label = if (sort.key == SortKey.TIME) "由新到旧" else "由大到小",
                        selected = sort.descending,
                        onClick = { onSortChange(sort.copy(descending = true)) }
                    )
                    SortChoiceChip(
                        label = if (sort.key == SortKey.TIME) "由旧到新" else "由小到大",
                        selected = !sort.descending,
                        onClick = { onSortChange(sort.copy(descending = false)) }
                    )
                }
            }
        }
    }
}

/** 内联排序选项：玻璃模式下是一颗玻璃 chip，选中时用朱砂描边 + 朱砂字。 */
@Composable
private fun SortChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val glass = LocalGlassEnabled.current
    Box(
        Modifier
            .clip(shape)
            .then(
                if (glass) {
                    Modifier.liquidGlassChip(
                        shape,
                        tint = if (selected) Vermilion.copy(alpha = 0.18f)
                        else BWTheme.colors.paper.copy(alpha = 0.45f)
                    )
                } else if (selected) {
                    Modifier.background(Vermilion.copy(alpha = 0.14f))
                } else {
                    Modifier
                }
            )
            .border(1.dp, if (selected) Vermilion.copy(alpha = 0.55f) else Rule, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) Vermilion else Muted,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun HeaderAction(text: String, onClick: () -> Unit, active: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    val glass = LocalGlassEnabled.current
    Box(
        Modifier
            .clip(shape)
            .then(
                if (glass) {
                    Modifier.liquidGlassChip(
                        shape,
                        tint = if (active) Vermilion.copy(alpha = 0.16f)
                        else BWTheme.colors.paper.copy(alpha = 0.45f)
                    )
                } else if (active) {
                    Modifier.background(Vermilion.copy(alpha = 0.12f))
                } else {
                    Modifier
                }
            )
            .border(1.dp, if (active) Vermilion.copy(alpha = 0.5f) else Rule, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            fontSize = 11.sp,
            color = if (active) Vermilion else Muted,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onSelectAll: () -> Unit, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("已选 $count 项", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSelectAll) { Text("全选", fontSize = 13.sp) }
        TextButton(onClick = onCancel) { Text("取消", fontSize = 13.sp) }
    }
}

@Composable
private fun SectionHeader(section: HistorySection) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            section.title,
            fontFamily = SerifFamily,
            fontSize = 12.sp,
            color = if (section.kind == SectionKind.PINNED) Vermilion else Muted,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.weight(1f))
        Text("${section.items.size} 条", fontSize = 11.sp, color = Muted.copy(alpha = 0.6f))
    }
}
