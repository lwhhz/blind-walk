package com.blindwalk.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import com.kyant.backdrop.backdrops.layerBackdrop
import com.blindwalk.app.ui.glass.LocalGlassBackdrop
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindwalk.app.BlindWalkApp
import com.blindwalk.app.location.TripService
import com.blindwalk.app.TripStore
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.home.HomeScreen
import com.blindwalk.app.ui.nav.AppTab
import com.blindwalk.app.ui.components.BwDialogHost
import com.blindwalk.app.ui.nav.BwBottomBar
import com.blindwalk.app.ui.summary.SummaryScreen
import com.blindwalk.app.ui.trash.TrashScreen
import com.blindwalk.app.ui.trip.TripIdleScreen
import com.blindwalk.app.ui.trip.TripScreen
import com.blindwalk.app.ui.wallpaper.ImageCropperDialog
import com.blindwalk.app.ui.wallpaper.LocalWallpaperAspect
import com.blindwalk.app.ui.wallpaper.PaperScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 浮层路由：总结页和回收站不属于任何一个 tab，盖在 tab 之上。 */
private const val OVERLAY_TRASH = "trash"
private const val OVERLAY_INFO = "info"
private const val OVERLAY_SUMMARY_PREFIX = "summary:"

private fun summaryRoute(id: String) = OVERLAY_SUMMARY_PREFIX + id

@Composable
fun BlindWalkRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as BlindWalkApp
    val glass = LocalGlassEnabled.current
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var overlay by rememberSaveable { mutableStateOf("") }
    var lastOverlay by rememberSaveable { mutableStateOf("") }
    var bootChecked by rememberSaveable { mutableStateOf(false) }
    var showPermissionHint by rememberSaveable { mutableStateOf(false) }
    var showLocationHint by rememberSaveable { mutableStateOf(false) }

    val tripRecord by TripStore.record.collectAsStateWithLifecycle()
    val tripRunning = tripRecord != null

    // ---------------------------------------------------------------- 窗纸
    val paperStore = remember(context) { app.container.wallpaper }
    val paperState by paperStore.state.collectAsStateWithLifecycle()
    var cropSource by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    // 窗纸页专用：只录实色纸底的一层 backdrop，玻璃采样它就不会显示壁纸
    val paperOnlyBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val paperThemeKey = paperState.themeMode

    val measuredAspect = LocalWallpaperAspect.current
    val screenAspect = remember(measuredAspect) {
        measuredAspect.takeIf { it > 0.1f } ?: run {
            val dm = context.resources.displayMetrics
            dm.widthPixels.toFloat() / dm.heightPixels.toFloat()
        }
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { decodeForCrop(context, uri) }
                if (bitmap != null) cropSource = bitmap
            }
        }
    }

    // ---------------------------------------------------------------- 开始行程
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        when {
            !fine -> showPermissionHint = true
            !isLocationEnabled(context) -> showLocationHint = true
            else -> {
                TripService.start(context)
                tab = AppTab.TRIP
            }
        }
    }

    fun startTrip() {
        when {
            !hasFineLocation(context) -> permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                    // 计步：拒了也不阻断行程，只是界面上不显示步数。
                    Manifest.permission.ACTIVITY_RECOGNITION
                )
            )

            !isLocationEnabled(context) -> showLocationHint = true
            else -> {
                TripService.start(context)
                tab = AppTab.TRIP
            }
        }
    }

    // 冷启动自愈：上次进程被杀时可能还有未结束的行程。
    LaunchedEffect(Unit) {
        if (bootChecked) return@LaunchedEffect
        bootChecked = true
        val unfinished = withContext(Dispatchers.IO) { app.container.repository.findUnfinished() }
        if (unfinished != null && hasFineLocation(context) && isLocationEnabled(context)) {
            TripService.start(context, recover = true)
            tab = AppTab.TRIP
        }
    }

    // 返回键：先收浮层，再回首页 tab。底栏存在的前提是"随时能回来"，所以这里不回退应用。
    BackHandler(enabled = overlay.isNotEmpty() || tab != AppTab.HOME) {
        if (overlay.isNotEmpty()) overlay = "" else tab = AppTab.HOME
    }

    // 弹窗打开时底栏隐藏（弹窗要占满整屏）
    val dialogOpen = remember { mutableStateOf(false) }

    val paperActive = tab == AppTab.PAPER
    // 纯色底：窗纸页本来就要；另外没有壁纸时所有页面也要铺纯色——
    // 空背景（只有纸底）下玻璃指示器的采样会和真实图标错开，看着像重影。
    val solidBackdrop = paperActive || !paperState.hasWallpaper
    // 窗纸页：纯色纸底整层淡入 / 淡出
    val paperCover = remember { androidx.compose.animation.core.Animatable(if (solidBackdrop) 1f else 0f) }
    LaunchedEffect(solidBackdrop) {
        paperCover.animateTo(
            if (solidBackdrop) 1f else 0f,
            androidx.compose.animation.core.tween(if (solidBackdrop) 260 else 220)
        )
    }

    CompositionLocalProvider(
        LocalGlassBackdrop provides
            if (solidBackdrop) paperOnlyBackdrop else LocalGlassBackdrop.current,
            com.blindwalk.app.ui.components.LocalDialogOpen provides dialogOpen
    ) {
    Box(Modifier.fillMaxSize()) {
        // 纯纸底：录制一份给玻璃采样（不可见）
        Box(
            Modifier
                .matchParentSize()
                .alpha(0f)
                .layerBackdrop(paperOnlyBackdrop)
                .background(com.blindwalk.app.ui.theme.BWTheme.colors.paper)
        )

        // 可见的一层：整层淡入 / 淡出
        if (paperCover.value > 0.001f) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(paperCover.value)
                    .background(com.blindwalk.app.ui.theme.BWTheme.colors.paper)
            )
        }
        // 内容区：给底栏留出空间，页面自己不用再算。
        // 浮层（回收站/总结）打开时内容整体隐去，好让浮层直接透出壁纸。
        Box(
            Modifier
                .fillMaxSize()
                
                .graphicsLayer { alpha = if (overlay.isEmpty()) 1f else 0f }
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (glass) {
                        // 玻璃：按 tab 顺序方向横移，和底栏指示器滑动的方向一致。
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(tween(340)) { it / 6 * dir } +
                                fadeIn(tween(240, delayMillis = 40))
                            ).togetherWith(
                            slideOutHorizontally(tween(340)) { -it / 8 * dir } +
                                fadeOut(tween(180))
                        )
                    } else {
                        // Material：现代标准做法，淡入 + 极轻的缩放，不做横向位移。
                        (
                            fadeIn(tween(260, delayMillis = 30)) +
                                scaleIn(tween(320), initialScale = 0.985f)
                            ).togetherWith(
                            fadeOut(tween(160)) +
                                scaleOut(tween(320), targetScale = 0.995f)
                        )
                    }
                },
                label = "tab"
            ) { current ->
                when (current) {
                    AppTab.HOME -> HomeScreen(
                        onOpen = { id -> overlay = summaryRoute(id) },
                        onOpenTrash = { overlay = OVERLAY_TRASH },
                        onResumeDiscarded = {
                            TripService.start(context, recover = true)
                            tab = AppTab.TRIP
                        },
                        onResumeTrip = {
                            // 进行中的记录：回行程页继续记录
                            TripService.start(context, recover = true)
                            tab = AppTab.TRIP
                        }
                    )

                    AppTab.TRIP -> if (tripRunning) {
                        TripScreen(
                            onFinished = { id -> overlay = summaryRoute(id) },
                            onDiscarded = { tab = AppTab.HOME }
                        )
                    } else {
                        TripIdleScreen(onStart = { startTrip() })
                    }

                    // 纯色底与玻璃采样源已在根层处理，这里只放页面本身
                    AppTab.PAPER -> PaperScreen(
                        state = paperState,
                        onDismiss = { tab = AppTab.HOME },
                        onPickImage = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onRemove = { paperStore.removeWallpaper() },
                        onVeil = { paperStore.setVeil(it) },
                        onBlur = { paperStore.setBlur(it) },
                        onTheme = { paperStore.setThemeMode(it) },
                        onGlass = { paperStore.setGlass(it) },
                        onAccentFromWallpaper = { paperStore.setAccentFromWallpaper(it) },
                        onOpenInfo = { overlay = OVERLAY_INFO }
                    )
                }
                }
        }

        // 浮层：总结页 / 回收站。全屏盖住底栏。
        if (overlay.isNotEmpty()) lastOverlay = overlay
        AnimatedVisibility(
            visible = overlay.isNotEmpty(),
            // 和窗纸页一致：整页淡入淡出，不做位移
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(220))
        ) {
            when {
                lastOverlay == OVERLAY_TRASH -> TrashScreen(onDone = { overlay = "" })
                lastOverlay == OVERLAY_INFO -> com.blindwalk.app.ui.info.InfoScreen(onDone = { overlay = "" })
                else -> SummaryScreen(
                    sessionId = lastOverlay.removePrefix(OVERLAY_SUMMARY_PREFIX),
                    onDone = { overlay = "" }
                )
            }
        }

        // 浮层（回收站 / 总结）盖住底栏
        if (overlay.isEmpty()) {
            // 底栏画在内容区里、页面之上：页面内容可以滑到它下面，
            // 玻璃会把底下的内容透出来。
            BwBottomBar(
                selected = tab,
                onSelect = { tab = it },
                tripRunning = tripRunning,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp)
            )
        }
    }

    // 弹窗层：最上层。遮罩从这里一路盖到底，底栏也会被压暗。
    BwDialogHost.content?.invoke()
    }

    cropSource?.let { source ->
        ImageCropperDialog(
            source = source,
            aspect = screenAspect,
            onCancel = { cropSource = null },
            onConfirm = { cropped ->
                cropSource = null
                if (!paperStore.saveWallpaper(cropped)) {
                    Toast.makeText(context, "壁纸保存失败，换个图再试试", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showPermissionHint) {
        AlertDialog(
            onDismissRequest = { showPermissionHint = false },
            title = { Text("需要精确位置") },
            text = { Text("瞎走要靠定位记录真实走过的路。请授予「精确位置」权限——只给大致位置的话，轨迹会糊成一团。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionHint = false
                    openAppSettings(context)
                }) { Text("去设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionHint = false }) { Text("算了") }
            }
        )
    }

    if (showLocationHint) {
        AlertDialog(
            onDismissRequest = { showLocationHint = false },
            title = { Text("定位服务没开") },
            text = { Text("请在系统设置里打开定位服务，否则记录不到轨迹。") },
            confirmButton = {
                TextButton(onClick = {
                    showLocationHint = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationHint = false }) { Text("取消") }
            }
        )
    }
}

/** 解码相册图片：先读尺寸再按 2 的幂降采样，避免大图直接解码吃爆内存。 */
private fun decodeForCrop(context: Context, uri: Uri, maxDim: Int = 3072): android.graphics.Bitmap? {
    return runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maxDim ||
            bounds.outHeight / (sample * 2) >= maxDim
        ) {
            sample *= 2
        }

        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()
}

private fun hasFineLocation(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
    runCatching { context.startActivity(intent) }
}
