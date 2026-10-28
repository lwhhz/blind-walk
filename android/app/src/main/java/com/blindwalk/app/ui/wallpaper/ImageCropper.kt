package com.blindwalk.app.ui.wallpaper

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.theme.SerifFamily
import com.blindwalk.app.ui.theme.Vermilion

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private val Backdrop = Color(0xFF0E0E0D)

/** 底部按钮区预留高度（px 换算前的 dp 参考值，实际按密度算）。 */
private val BottomBarReserve = 96.dp

/**
 * 裁剪页：图片拖到窗口里，双指缩放，窗口比例锁死为设备屏幕比例。
 *
 * 为什么自己写而不用第三方裁剪库：需求就是"锁比例 + 缩放拖动"，
 * uCrop 之类会带进来额外依赖和一堆用不上的功能，与这个工程的极简取向冲突。
 */
@Composable
fun ImageCropperDialog(
    source: Bitmap,
    aspect: Float,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    val image = remember(source) { source.asImageBitmap() }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Backdrop)
                // 这个 Dialog 关掉了 decorFitsSystemWindows，不补 inset 的话
                // 顶栏会钻到状态栏底下、底部按钮会贴着手势条。
                .safeDrawingPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "取消",
                    fontSize = 14.sp,
                    color = Color(0xFF9C958B),
                    modifier = Modifier
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "调整壁纸",
                    fontFamily = SerifFamily,
                    fontSize = 18.sp,
                    // 背景是固定的近黑色，文字不能跟随主题：墨纸模式下
                    // 调色板的"纸色"本身是深色，会直接看不见。
                    color = Color(0xFFF6F2E9)
                )
                Spacer(Modifier.weight(1f))
                // 占位，让标题真正居中
                Spacer(Modifier.size(44.dp, 1.dp))
            }

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                val density = LocalDensity.current
                val reserve = with(density) { BottomBarReserve.toPx() }
                val availW = constraints.maxWidth.toFloat()
                val availH = (constraints.maxHeight.toFloat() - reserve).coerceAtLeast(1f)

                val frameW: Float
                val frameH: Float
                if (availW / availH > aspect) {
                    frameH = availH
                    frameW = frameH * aspect
                } else {
                    frameW = availW
                    frameH = frameW / aspect
                }

                val iw = image.width.toFloat()
                val ih = image.height.toFloat()
                val baseScale = maxOf(frameW / iw, frameH / ih)

                fun clampPan(p: Offset, scale: Float): Offset {
                    val dispW = iw * baseScale * scale
                    val dispH = ih * baseScale * scale
                    val maxX = ((dispW - frameW) / 2f).coerceAtLeast(0f)
                    val maxY = ((dispH - frameH) / 2f).coerceAtLeast(0f)
                    return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                }

                Box(Modifier.fillMaxSize()) {
                    // 裁剪窗口
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .size(
                                with(density) { frameW.toDp() },
                                with(density) { frameH.toDp() }
                            )
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                                    val nextZoom = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                    zoom = nextZoom
                                    pan = clampPan(pan + gesturePan, nextZoom)
                                }
                            }
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                // 必须用 requiredSize 而不是 size：
                                // size 会被父容器（裁剪窗口）的约束压回去，图片就会被硬塞进窗口、
                                // 被 FillBounds 拉伸变形，而裁剪计算是按"原比例居中裁取"来的——
                                // 两者对不上，预览和结果自然不一致。
                                // requiredSize 忽略传入约束，图片保持原比例溢出窗口，再由 clipToBounds 裁掉。
                                .requiredSize(
                                    with(density) { (iw * baseScale).toDp() },
                                    with(density) { (ih * baseScale).toDp() }
                                )
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                }
                        ) {
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "双指缩放 · 拖动调整 · 窗口已锁定为屏幕比例",
                            fontSize = 12.sp,
                            color = Color(0xFF9C958B)
                        )
                        Spacer(Modifier.height(14.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Vermilion)
                                .clickable {
                                    onConfirm(
                                        cropToAspect(
                                            source = source,
                                            frameWpx = frameW,
                                            frameHpx = frameH,
                                            baseScale = baseScale,
                                            zoom = zoom,
                                            pan = pan
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // 底色是朱砂，文字固定用纸白——不能跟随主题，
                            // 墨纸模式下调色板的"纸色"是深色，红底深字会糊成一片。
                            Text(
                                "使用这张",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF6F2E9)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * 把窗口里看到的那一块按比例截出来。
 *
 * 窗口中心对应的源图坐标 = 图片中心 − 平移量 / 总缩放。
 */
private fun cropToAspect(
    source: Bitmap,
    frameWpx: Float,
    frameHpx: Float,
    baseScale: Float,
    zoom: Float,
    pan: Offset
): Bitmap {
    if (frameWpx <= 0f || frameHpx <= 0f) return source

    val total = baseScale * zoom
    val iw = source.width.toFloat()
    val ih = source.height.toFloat()

    val srcW = (frameWpx / total).coerceAtMost(iw)
    val srcH = (frameHpx / total).coerceAtMost(ih)

    val centerX = iw / 2f - pan.x / total
    val centerY = ih / 2f - pan.y / total

    val left = (centerX - srcW / 2f).coerceIn(0f, (iw - srcW).coerceAtLeast(0f))
    val top = (centerY - srcH / 2f).coerceIn(0f, (ih - srcH).coerceAtLeast(0f))

    val crop = Bitmap.createBitmap(
        source,
        left.toInt(),
        top.toInt(),
        srcW.toInt().coerceAtLeast(1),
        srcH.toInt().coerceAtLeast(1)
    )

    // 短边封顶 1080（手机屏幕的典型短边），不放大：
    // 上采样只会让 JPEG 变大变糊，不会多出细节。
    val shortSide = minOf(crop.width, crop.height)
    val targetShort = 1080
    return if (shortSide <= targetShort) {
        crop
    } else {
        val scale = targetShort.toFloat() / shortSide.toFloat()
        Bitmap.createScaledBitmap(
            crop,
            (crop.width * scale).toInt().coerceAtLeast(1),
            (crop.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
