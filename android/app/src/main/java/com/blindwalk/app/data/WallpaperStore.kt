package com.blindwalk.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

/** 主题三档：跟随系统 / 白纸 / 墨纸。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("白纸"),
    DARK("墨纸")
}

@Immutable
data class WallpaperState(
    /** 壁纸文件的绝对路径；null 表示没有自定义壁纸，走纯纸底。 */
    val path: String? = null,
    /** 遮罩：负数加暗、正数加亮，范围 -80..80。 */
    val veil: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 高斯模糊半径（dp），0 表示不模糊。 */
    val blur: Int = 0,
    /** 液态玻璃（实验性）。关闭时所有玻璃控件退回实色。 */
    val glass: Boolean = false,
    /** 主题色跟随壁纸：从壁纸取主色，替换朱砂。 */
    val accentFromWallpaper: Boolean = false,
    /**
     * 换图版本号。路径每次都是同一个文件，光看 path 无法判断"图换了"，
     * 所以用一个递增标记让图片加载知道该重新解码。
     */
    val revision: Long = 0L
) {
    val hasWallpaper: Boolean get() = path != null
}

/**
 * 壁纸与主题设置的持久化。
 *
 * 只存文件路径而不是 URI：相册返回的 content:// 授权在进程重启后就失效了，
 * 所以选完图立刻复制一份到私有目录，之后只读自己的文件。
 */
class WallpaperStore(private val context: Context) {

    private companion object {
        const val PREFS = "blindwalk_paper"
        const val KEY_PATH = "path"
        const val KEY_VEIL = "veil"
        const val KEY_THEME = "theme"
        const val KEY_BLUR = "blur"
        const val KEY_GLASS = "glass"
        const val KEY_ACCENT_FROM_WALLPAPER = "accent_from_wallpaper"
        const val VEIL_MIN = -80
        const val VEIL_MAX = 80
        const val BLUR_MAX = 40
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<WallpaperState> = _state.asStateFlow()

    /** 壁纸固定落在私有目录，文件名写死，换图直接覆盖，不会攒下一堆历史文件。 */
    val imageFile: File
        get() = File(context.filesDir, "wallpaper/paper.jpg")

    private fun read(): WallpaperState {
        val path = prefs.getString(KEY_PATH, null)?.takeIf { File(it).isFile }
        val veil = prefs.getInt(KEY_VEIL, 0).coerceIn(VEIL_MIN, VEIL_MAX)
        val mode = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        val revision = path?.let { File(it).lastModified() } ?: 0L
        val blur = prefs.getInt(KEY_BLUR, 0).coerceIn(0, BLUR_MAX)
        val glass = prefs.getBoolean(KEY_GLASS, false)
        val accentFromWallpaper = prefs.getBoolean(KEY_ACCENT_FROM_WALLPAPER, false)
        return WallpaperState(
            path = path,
            veil = veil,
            themeMode = mode,
            blur = blur,
            glass = glass,
            accentFromWallpaper = accentFromWallpaper,
            revision = revision
        )
    }

    fun setGlass(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GLASS, enabled).apply()
        _state.value = _state.value.copy(glass = enabled)
    }

    fun setAccentFromWallpaper(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACCENT_FROM_WALLPAPER, enabled).apply()
        _state.value = _state.value.copy(accentFromWallpaper = enabled)
    }

    fun setBlur(value: Int) {
        val v = value.coerceIn(0, BLUR_MAX)
        prefs.edit().putInt(KEY_BLUR, v).apply()
        _state.value = _state.value.copy(blur = v)
    }

    fun setVeil(value: Int) {
        val v = value.coerceIn(VEIL_MIN, VEIL_MAX)
        prefs.edit().putInt(KEY_VEIL, v).apply()
        _state.value = _state.value.copy(veil = v)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _state.value = _state.value.copy(themeMode = mode)
    }

    /** 写入裁剪好的位图并立即生效。返回是否成功。 */
    fun saveWallpaper(bitmap: Bitmap): Boolean {
        val target = imageFile
        val firstTime = _state.value.path == null
        return runCatching {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "paper.tmp")
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            setPath(target.absolutePath)
            // 第一次选图时给一个默认暗度：大多数照片直接用会让标题和统计变得很难读，
            // 与其让用户自己发现滑块，不如先给一个能看清的起点，随时可以拖回"原样"。
            if (firstTime && _state.value.veil == 0) setVeil(-35)
            true
        }.getOrDefault(false)
    }

    fun removeWallpaper() {
        prefs.edit().remove(KEY_PATH).apply()
        _state.value = _state.value.copy(path = null)
        runCatching { imageFile.delete() }
    }

    private fun setPath(path: String) {
        prefs.edit().putString(KEY_PATH, path).apply()
        _state.value = _state.value.copy(
            path = path,
            revision = File(path).lastModified()
        )
    }
}
