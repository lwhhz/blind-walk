package com.blindwalk.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.blindwalk.app.R

/**
 * 应用调色板。
 *
 * 亮色是"纸与墨"：纸白底、墨黑字、朱砂点睛。
 * 暗色不是简单反转——纸变成夜里的墨，字变成旧纸的白，
 * 朱砂略微提亮（#C8452D 在深底上偏闷），其余对比关系保持一致。
 */
@Immutable
data class BwPalette(
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val vermilion: Color,
    val surface: Color,
    val surfaceSelected: Color,
    val actionNeutral: Color
)

val LightPalette = BwPalette(
    paper = Color(0xFFF6F2E9),
    ink = Color(0xFF191918),
    // 浅色下的次要文字。原来的 #8A8378 在纸底上只有 3.36:1，
    // 小字号（11~13sp）根本达不到 WCAG AA 的 4.5:1，屏幕上就是"灰得发虚"。
    // #6B6459 在纸底 5.23:1、卡片底 4.83:1，两头都过线，观感仍是灰而不是黑。
    muted = Color(0xFF6B6459),
    rule = Color(0xFFDCD5C6),
    vermilion = Color(0xFFC8452D),
    surface = Color(0xFFEFE9DC),
    surfaceSelected = Color(0xFFE5DFD0),
    actionNeutral = Color(0xFF8A8378)
)

val DarkPalette = BwPalette(
    paper = Color(0xFF141413),
    ink = Color(0xFFF2EDE3),
    muted = Color(0xFF9C958B),
    rule = Color(0xFF34302B),
    vermilion = Color(0xFFE0603F),
    surface = Color(0xFF1E1D1B),
    surfaceSelected = Color(0xFF2B2825),
    actionNeutral = Color(0xFF7C756B)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * 强调色覆盖。默认 null = 用调色板自带的朱砂。
 * 「主题色跟随壁纸」开启时，由上层从壁纸里取出主色塞进来。
 */
val LocalAccentOverride = staticCompositionLocalOf<Color?> { null }

/**
 * 当前是否深色。
 * 有些地方需要"知道自己是深还是浅"而不只是拿颜色——比如总结页导出卡片的默认明暗。
 */
val LocalIsDark = staticCompositionLocalOf { false }

object BWTheme {
    val colors: BwPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalPalette.current
}

/**
 * 中文衬线：Noto Serif SC 的 GB2312 一级字子集。
 * 完整字重 11.1MB，裁到 3755 常用字后 1.18MB。
 * 子集之外的生僻字由系统字体回落，不会出现方框。
 *
 * 只打包 Regular：Bold 源文件下载始终不完整，强行塞一个"假 Bold"
 * 会让字重标注骗人。需要粗体时由系统合成（Compose 会自动处理）。
 */
val SerifFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.noto_serif_sc_regular, FontWeight.Normal)
)

/** 数字用等宽，避免计时器每秒跳动时宽度抖动。 */
val NumericFamily = FontFamily.Monospace

private fun buildTypography(palette: BwPalette) = Typography(
    displayLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 54.sp, letterSpacing = 2.sp, color = palette.ink),
    headlineLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 40.sp, letterSpacing = 2.sp, color = palette.ink),
    headlineMedium = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, letterSpacing = 1.sp, color = palette.ink),
    titleLarge = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, letterSpacing = 1.sp, color = palette.ink),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, color = palette.ink),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, color = palette.muted),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, letterSpacing = 1.sp, color = palette.muted)
)

private fun schemeFor(palette: BwPalette) = if (palette.paper.luminance() > 0.5f) {
    lightColorScheme(
        primary = palette.ink,
        onPrimary = palette.paper,
        secondary = palette.vermilion,
        onSecondary = palette.paper,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.paper,
        onSurface = palette.ink,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.muted,
        outline = palette.rule
    )
} else {
    darkColorScheme(
        primary = palette.ink,
        onPrimary = palette.paper,
        secondary = palette.vermilion,
        onSecondary = palette.paper,
        background = palette.paper,
        onBackground = palette.ink,
        surface = palette.paper,
        onSurface = palette.ink,
        surfaceVariant = palette.surface,
        onSurfaceVariant = palette.muted,
        outline = palette.rule
    )
}

private fun Color.luminance(): Float {
    fun channel(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

@Composable
fun BlindWalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkPalette else LightPalette
    val accent = LocalAccentOverride.current
    // 只换强调色；纸、墨、分隔线仍由主题决定
    val palette = if (accent != null) base.copy(vermilion = accent) else base
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalIsDark provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = schemeFor(palette),
            typography = buildTypography(palette),
            content = content
        )
    }
}

// ---------------------------------------------------------------- 便捷访问
// 保留原来的短名字，界面代码几乎不用改。

val Paper: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.paper

val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink

val Muted: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.muted

val Rule: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.rule

val Vermilion: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.vermilion
