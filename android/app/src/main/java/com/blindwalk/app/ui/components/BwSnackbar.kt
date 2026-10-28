package com.blindwalk.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.glass.liquidGlassChip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用内小通知。
 *
 * 不用 Material3 的 Snackbar：它的深灰圆角块跟这套纸/墨调色板毫无关系，
 * 尤其在暗色模式下像一块外来弹窗。这里做成一张"便签条"——
 * 纸色底、1dp 墨色描边、接近方角的 6dp 圆角，操作文字用朱砂。
 *
 * 4 秒后自动消失；超时和点操作是互斥的两条出口。
 */
class BwSnackbarState {

    data class Message(
        val id: Long,
        val text: String,
        val action: String?,
        val onAction: (() -> Unit)?,
        val onTimeout: (() -> Unit)?
    )

    var current by mutableStateOf<Message?>(null)
        private set

    /** 退出动画期间仍要显示旧内容，所以单独留一份。 */
    var last by mutableStateOf<Message?>(null)
        private set

    private var job: Job? = null

    fun show(
        scope: CoroutineScope,
        text: String,
        action: String? = null,
        onAction: (() -> Unit)? = null,
        onTimeout: (() -> Unit)? = null,
        durationMillis: Long = 4_000L
    ) {
        job?.cancel()
        val message = Message(System.nanoTime(), text, action, onAction, onTimeout)
        current = message
        last = message
        job = scope.launch {
            delay(durationMillis)
            if (current?.id == message.id) {
                dismiss()
                onTimeout?.invoke()
            }
        }
    }

    fun performAction() {
        val message = current ?: return
        dismiss()
        message.onAction?.invoke()
    }

    fun dismiss() {
        job?.cancel()
        job = null
        current = null
    }
}

@Composable
fun BwSnackbarHost(
    state: BwSnackbarState,
    modifier: Modifier = Modifier
) {
    val visible = state.current != null
    val shown = state.current ?: state.last

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier
    ) {
        if (shown == null) return@AnimatedVisibility
        val shape = RoundedCornerShape(6.dp)
        val glass = com.blindwalk.app.ui.glass.LocalGlassEnabled.current
        val fill = BWTheme.colors.paper.copy(alpha = com.blindwalk.app.ui.glass.GLASS_TINT_ALPHA)
        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (glass) {
                        Modifier.liquidGlassChip(shape, tint = fill, pressable = false)
                    } else {
                        Modifier.background(BWTheme.colors.paper, shape)
                    }
                )
                .border(1.dp, BWTheme.colors.ink.copy(alpha = 0.85f), shape)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    shown.text,
                    fontSize = 13.sp,
                    color = BWTheme.colors.ink,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (shown.action != null) {
                    Text(
                        shown.action,
                        fontSize = 13.sp,
                        color = BWTheme.colors.vermilion,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { state.performAction() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
