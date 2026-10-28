package com.blindwalk.app.ui.info

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextDecoration
import com.blindwalk.app.R
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.glass.liquidGlass
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.SerifFamily

/**
 * 窗纸页右上角的圆形入口：显示用户提供的「书书」图标，染色后跟主题色走。
 *
 * 染色用正片叠底（Modulate）：原图是白色笔触，乘上主题色就得到主题色的笔刷，
 * 浓淡和毛边都还在。换成 SrcIn 剪影会把笔触糊成一片实心块。
 */
@Composable
fun ShushuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlassEnabled.current
    val accent = BWTheme.colors.vermilion
    val shape = CircleShape

    Box(
        modifier
            .size(44.dp)
            .clip(shape)
            .then(
                if (glass) {
                    Modifier.liquidGlass(shape, tint = BWTheme.colors.paper.copy(alpha = 0.45f))
                } else {
                    // 关掉液态玻璃时退回 Material 的 tonal 圆底
                    Modifier.background(BWTheme.colors.surface)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_shushu),
            contentDescription = "应用信息",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(28.dp),
            colorFilter = ColorFilter.tint(accent, BlendMode.Modulate)
        )
    }
}

/** 应用信息页：全屏浮层，和回收站/总结页同一套进出场。 */
@Composable
fun InfoScreen(onDone: () -> Unit) {
    val glass = LocalGlassEnabled.current
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .then(if (glass) Modifier else Modifier.background(BWTheme.colors.paper))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 86.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "关于",
                    fontFamily = SerifFamily,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 6.sp,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "完成",
                    fontSize = 14.sp,
                    color = Muted,
                    modifier = Modifier
                        .clickable(onClick = onDone)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "瞎走 · Blind Walk",
                fontSize = 13.sp,
                color = Muted,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(20.dp))

            // ---------------------------------------------------------- 版本
            InfoCard {
                InfoRow("版本", "1.0.0（10）")
                InfoDivider()
                InfoRow("构建", "release · 3.29 MB")
                InfoDivider()
                Text(
                    "按下按钮，它给你一个方向。你走，它记录。走完给你一张卡片。",
                    fontSize = 13.sp,
                    color = Muted,
                    lineHeight = 21.sp
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("开源")
            Spacer(Modifier.height(10.dp))
            InfoCard {
                LinkRow(
                    label = "源码仓库",
                    value = "github.com/lwhhz/blind-walk",
                    onClick = { openUrl(context, "https://github.com/lwhhz/blind-walk") }
                )
                InfoDivider()
                LinkRow(
                    label = "问题反馈",
                    value = "github.com/lwhhz/blind-walk/issues",
                    onClick = { openUrl(context, "https://github.com/lwhhz/blind-walk/issues") }
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("隐私与权限")
            Spacer(Modifier.height(10.dp))
            InfoCard {
                Text(
                    "完全离线。App 自身不声明 INTERNET 权限——所有数据只留在这台设备上，" +
                        "卸载即删除。仓库链接走系统浏览器打开，不经过 App。",
                    fontSize = 13.sp,
                    color = Muted,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(12.dp))
                PermissionRow("精确位置", "记录里程与轨迹，只在行程进行中")
                PermissionRow("前台服务", "屏幕关掉也能继续记录，通知栏常驻")
                PermissionRow("通知", "显示行程状态与暂停入口")
                PermissionRow("身体活动", "读取系统计步器，没有就不显示步数")
                PermissionRow("震动", "抽到方向时的触感反馈")
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("开源许可")
            Spacer(Modifier.height(10.dp))
            InfoCard {
                LicenseRow("Kotlin · Compose · AndroidX", "Apache License 2.0")
                InfoDivider()
                LicenseRow("AndroidLiquidGlass (Kyant0)", "Apache License 2.0")
                InfoDivider()
                LicenseRow("Noto Serif SC", "SIL Open Font License 1.1")
                InfoDivider()
                LicenseRow("本应用", "MIT License")
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("致谢")
            Spacer(Modifier.height(10.dp))
            InfoCard {
                Text(
                    "液态玻璃的折射、色散与高光来自 Kyant0 的 AndroidLiquidGlass；" +
                        "底栏的拖拽交互参考了它的示例实现。",
                    fontSize = 13.sp,
                    color = Muted,
                    lineHeight = 21.sp
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "© 2026 lwhhz",
                fontSize = 12.sp,
                color = Muted,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ------------------------------------------------------------------ 小部件

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Muted,
        letterSpacing = 2.sp
    )
}

@Composable
private fun InfoCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val glass = LocalGlassEnabled.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (glass) {
                    Modifier.liquidGlass(shape, tint = BWTheme.colors.paper.copy(alpha = 0.45f))
                } else {
                    Modifier
                        .background(BWTheme.colors.surface)
                        .border(1.dp, Rule, shape)
                }
            )
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Ink)
        Text(value, fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(label, fontSize = 14.sp, color = Ink)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            fontSize = 12.sp,
            color = BWTheme.colors.vermilion,
            textDecoration = TextDecoration.Underline
        )
    }
}

@Composable
private fun PermissionRow(name: String, detail: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, fontSize = 13.sp, color = Ink, modifier = Modifier.width(72.dp))
        Text(detail, fontSize = 12.sp, color = Muted, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LicenseRow(name: String, license: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            license,
            fontSize = 12.sp,
            color = Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun InfoDivider() {
    Spacer(Modifier.height(10.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Rule.copy(alpha = 0.6f))
    )
    Spacer(Modifier.height(10.dp))
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
