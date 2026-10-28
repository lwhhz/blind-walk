package com.blindwalk.app.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 从壁纸里取一个"主色"当作强调色。
 *
 * 做法：把图缩到 64×64，按 4bit/通道（4096 个桶）统计，
 * 先剔除接近灰、过暗、过亮的像素（它们做强调色会发闷或刺眼），
 * 再取像素最多的那个桶，对桶内像素求平均，最后把明度和饱和度推到合适区间。
 *
 * 返回 null 表示取不到（没有图、图几乎无色），调用方回落到朱砂。
 */
fun dominantAccentColor(image: ImageBitmap, dark: Boolean): Color? {
    val small = runCatching {
        Bitmap.createScaledBitmap(image.asAndroidBitmap(), 64, 64, true)
    }.getOrNull() ?: return null

    val pixels = IntArray(64 * 64)
    small.getPixels(pixels, 0, 64, 0, 0, 64, 64)
    if (small !== image.asAndroidBitmap()) small.recycle()

    // 桶 -> [数量, rSum, gSum, bSum]
    val counts = IntArray(4096)
    val rSum = IntArray(4096)
    val gSum = IntArray(4096)
    val bSum = IntArray(4096)

    val hsv = FloatArray(3)
    for (p in pixels) {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        AndroidColor.RGBToHSV(r, g, b, hsv)
        // 灰、过暗、过亮的像素当强调色都不好看
        if (hsv[1] < 0.22f || hsv[2] < 0.16f || hsv[2] > 0.94f) continue
        val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
        counts[key]++
        rSum[key] += r
        gSum[key] += g
        bSum[key] += b
    }

    var best = -1
    var bestCount = 0
    for (i in counts.indices) {
        if (counts[i] > bestCount) {
            bestCount = counts[i]
            best = i
        }
    }
    // 有效像素太少，说明这张图基本是灰的
    if (best < 0 || bestCount < pixels.size / 80) return null

    val n = counts[best].toFloat()
    val r = (rSum[best] / n).roundToInt()
    val g = (gSum[best] / n).roundToInt()
    val b = (bSum[best] / n).roundToInt()

    AndroidColor.RGBToHSV(r, g, b, hsv)
    // 饱和度托底，避免取到一块"脏色"；明度按主题定：
    // 深色纸要亮一点的强调色才跳得出来，浅色纸反之。
    hsv[1] = hsv[1].coerceIn(0.55f, 0.92f)
    hsv[2] = if (dark) 0.74f else 0.55f
    return Color(AndroidColor.HSVToColor(hsv))
}

/**
 * 读取壁纸并算出强调色。开关关掉时直接返回 null，不做任何解码。
 */
@Composable
fun rememberWallpaperAccent(
    path: String?,
    revision: Long,
    dark: Boolean,
    enabled: Boolean
): Color? {
    val bitmap = rememberWallpaperBitmap(if (enabled) path else null, revision)
    val state = produceState<Color?>(initialValue = null, bitmap, dark, enabled) {
        val bmp = bitmap
        value = if (!enabled || bmp == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching { dominantAccentColor(bmp, dark) }.getOrNull()
            }
        }
    }
    return state.value
}
