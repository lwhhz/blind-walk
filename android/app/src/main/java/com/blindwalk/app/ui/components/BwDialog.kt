package com.blindwalk.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.glass.liquidGlass
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.theme.SerifFamily

/**
 * 统一的弹窗外壳。
 *
 * 为什么不用 `Dialog`：那是一个独立的系统窗口，而液态玻璃要采样的背景层属于 Activity 窗口，
 * 跨窗口取坐标会错位，玻璃里会看到一片错位的背景。
 * 所以这里做成**窗口内浮层**：铺在调用方根布局的最后一层，遮罩 + 居中卡片。
 * 顺带的好处是圆角、遮罩透明度、动画都完全可控。
 *
 * 内容超出屏幕时卡片内部可滚动，不会把按钮顶出可视区。
 */
/** 弹窗是否打开（保留给需要感知的地方）。 */
val LocalDialogOpen = staticCompositionLocalOf { mutableStateOf(false) }

/**
 * 弹窗宿主：BwDialog 把卡片「寄存」到这里，由 BlindWalkRoot 在最上层渲染。
 *
 * 之所以要提到根层：底栏现在画在内容区里，如果弹窗还在页面内部，
 * 遮罩就会被底栏压住，底栏也就遮不暗。放到根层之后，
 * 一层遮罩从顶盖到底，底栏自然一起压暗。
 */
object BwDialogHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        internal set
}

@Composable
fun BwDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismissRequest)
    DisposableEffect(Unit) {
        BwDialogHost.content = { BwDialogSurface(onDismissRequest, content) }
        onDispose { BwDialogHost.content = null }
    }
}

@Composable
private fun BwDialogSurface(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val glass = LocalGlassEnabled.current
    val palette = BWTheme.colors
    val maxCard = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    Box(
        Modifier
            .fillMaxSize()
            // 遮罩要够重：玻璃控件采样的是背景层，不受遮罩影响，
            // 遮罩一旦偏淡，弹窗外那些玻璃按钮就会"亮着"，看着像没遮住。
            .background(Color.Black.copy(alpha = if (glass) 0.45f else 0.60f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismissRequest
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = maxCard)
                .clip(shape)
                .then(
                    if (glass) {
                        // 弹窗卡片本身不是可点的控件，按压高光留给里面的按钮。
                        Modifier.liquidGlass(
                            shape,
                            tint = palette.paper.copy(alpha = 0.35f),
                            pressable = false
                        )
                    } else {
                        Modifier.background(palette.paper)
                    }
                )
                // 拦住点击，避免在卡片内部点一下就触发遮罩的"关闭"
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                content = content
            )
        }
    }
}

@Composable
fun BwDialogTitle(text: String) {
    Text(
        text,
        fontFamily = SerifFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal,
        color = BWTheme.colors.ink
    )
}

@Composable
fun BwDialogActions(
    cancelLabel: String = "取消",
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmDestructive: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            cancelLabel,
            fontSize = 14.sp,
            color = BWTheme.colors.muted,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
        Text(
            confirmLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (confirmDestructive) BWTheme.colors.vermilion else BWTheme.colors.ink,
            modifier = Modifier
                .clickable(onClick = onConfirm)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
