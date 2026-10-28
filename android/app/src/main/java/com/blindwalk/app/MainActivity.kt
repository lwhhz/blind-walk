package com.blindwalk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blindwalk.app.data.ThemeMode
import com.blindwalk.app.ui.BlindWalkRoot
import com.blindwalk.app.ui.glass.LocalGlassBackdrop
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.theme.BlindWalkTheme
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.wallpaper.LocalWallpaperActive
import com.blindwalk.app.ui.wallpaper.LocalWallpaperAspect
import com.blindwalk.app.ui.wallpaper.WallpaperLayer
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = (application as BlindWalkApp).container.wallpaper

        setContent {
            val paper by store.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (paper.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 液态玻璃要采样的"背景"必须是干净的一层：纸底 + 壁纸。
            // 所以这里分两层——底层录进 GraphicsLayer，上层才是界面本身。
            val backdrop = rememberLayerBackdrop()
            var aspect by remember { mutableFloatStateOf(0f) }

            // 主题色跟随壁纸：开关打开且取到主色时才覆盖朱砂
            val accent = com.blindwalk.app.ui.wallpaper.rememberWallpaperAccent(
                path = paper.path,
                revision = paper.revision,
                dark = dark,
                enabled = paper.accentFromWallpaper
            )

            CompositionLocalProvider(
                com.blindwalk.app.ui.theme.LocalAccentOverride provides accent
            ) {
            BlindWalkTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalWallpaperActive provides paper.hasWallpaper,
                    LocalWallpaperAspect provides aspect,
                    LocalGlassEnabled provides paper.glass,
                    LocalGlassBackdrop provides backdrop
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { size ->
                                if (size.height > 0) {
                                    aspect = size.width.toFloat() / size.height.toFloat()
                                }
                            }
                    ) {
                        // 被录制的一层：纸底 + 壁纸，不含任何界面元素。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Paper)
                                .layerBackdrop(backdrop)
                        ) {
                            WallpaperLayer(state = paper, dark = dark)
                        }

                        // 界面层：玻璃控件从上面那层取样。
                        Box(Modifier.fillMaxSize()) {
                            BlindWalkRoot()
                        }
                    }
                }
            }
            }
        }
    }
}
