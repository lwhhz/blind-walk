package com.blindwalk.app.ui.wallpaper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.blindwalk.app.data.WallpaperState
import com.blindwalk.app.ui.theme.BWTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 当前是否铺着自定义壁纸。
 * 行程页那个朱砂大按钮要靠它决定是否降低不透明度——
 * 壁纸再好看也不能盖住"指路"这个主操作。
 */
val LocalWallpaperActive = staticCompositionLocalOf { false }

/**
 * 壁纸实际被绘制的宽高比（宽/高）。
 *
 * 裁剪页必须用它，而不是 `displayMetrics`：壁纸铺在窗口上，
 * 窗口尺寸和"屏幕尺寸"在不少机型上并不相等（状态栏/导航栏、挖孔、显示缩放），
 * 一旦两个比例有差，裁出来的图再被 ContentScale.Crop 二次裁切，就会和预览对不上。
 */
val LocalWallpaperAspect = staticCompositionLocalOf { 0f }

/**
 * 读壁纸位图。RGB_565：一张 1080×2400 用 ARGB_8888 要 10MB，565 只要 5MB，
 * 而壁纸不需要 alpha 通道。
 */
@Composable
fun rememberWallpaperBitmap(path: String?, revision: Long = 0L): ImageBitmap? {
    val key = path to revision
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                path?.let {
                    BitmapFactory.decodeFile(
                        it,
                        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    )?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    return bitmap
}

/**
 * 壁纸层：铺图 + 亮度调节。放在所有页面之下、内容之上。
 *
 * 调节算法（纯亮度乘法，不是叠黑/白幕）：
 *  - 增益 = 1 + veil/100，veil 范围 -80..80 → 0.20 ~ 1.80 倍。
 *    乘法保留对比度，不会像叠幕那样把画面整体拉成灰雾。
 *  - 深色主题额外乘一个 0.78 的系数：墨纸底配亮壁纸最刺眼，
 *    这一条是主题规则，不占用用户的滑块档位。
 *  - 增益下限 0.12：再暗壁纸就跟纯色底没区别了。
 */
fun veilGain(veil: Int, dark: Boolean): Float {
    val userGain = 1f + veil / 100f
    val themeGain = if (dark) 0.78f else 1f
    return (userGain * themeGain).coerceIn(0.12f, 2.2f)
}

/** 把亮度增益做成 ColorFilter，多个地方（壁纸层、预览）共用。 */
fun brightnessFilter(gain: Float): ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(gain, gain, gain, 1f) })

@Composable
fun WallpaperLayer(state: WallpaperState, dark: Boolean, modifier: Modifier = Modifier) {
    if (state.path == null) return
    val bitmap = rememberWallpaperBitmap(state.path, state.revision)

    Box(modifier.fillMaxSize().clipToBounds()) {
        bitmap?.let {
            val gain = veilGain(state.veil, dark)
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { colorFilter = brightnessFilter(gain) }
                    .then(
                        if (state.blur > 0) {
                            // 用 Rectangle 而不是 Unbounded：Unbounded 会让内容向外采样到"透明"，
                            // 边缘出现一圈毛边，之前为了遮它把图放大了 8%——
                            // 结果就是"应用后比预览更放大"，裁出来的边缘被吃掉。
                            // Rectangle 把模糊裁在自身边界内，内容完全不动。
                            Modifier.blur(
                                radius = state.blur.dp,
                                edgeTreatment = BlurredEdgeTreatment.Rectangle
                            )
                        } else Modifier
                    ),
                contentScale = ContentScale.Crop
            )
        }

        // 顶部渐隐：标题与统计直接压在照片上时，靠亮度滑块不一定救得回来。
        // 用主题的纸色做渐变——浅色模式下是往白里提，深色模式下是往黑里压，
        // 两种情况都是在给顶部的文字"垫一层底"，不需要按模式写两套。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to BWTheme.colors.paper.copy(alpha = 0.62f),
                        0.34f to BWTheme.colors.paper.copy(alpha = 0f)
                    )
                )
        )
    }
}
