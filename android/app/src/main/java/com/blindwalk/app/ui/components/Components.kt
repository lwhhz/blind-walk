package com.blindwalk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.domain.model.SessionStatus
import com.blindwalk.app.ui.glass.LocalGlassEnabled
import com.blindwalk.app.ui.glass.GLASS_TINT_ALPHA
import com.kyant.backdrop.highlight.Highlight
import com.blindwalk.app.ui.glass.liquidGlass
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.Vermilion

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    fontSize: TextUnit = 15.sp,
    /** 描边开关。玻璃模式下描边会变成一圈硬边，主操作区通常不需要。 */
    bordered: Boolean = true
) {
    val contentColor = if (enabled) {
        if (filled) Paper else Ink
    } else {
        Muted.copy(alpha = 0.5f)
    }

    val shape = RoundedCornerShape(28.dp)
    val glass = LocalGlassEnabled.current
    // 实心按钮统一 62% 不透明度，和「窗纸」卡片一致：壁纸能透出来，但字仍然压得住。
    val fill = if (enabled) Ink.copy(alpha = GLASS_TINT_ALPHA)
    else Ink.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .then(
                when {
                    filled && glass -> Modifier.liquidGlass(shape, tint = fill)
                    filled -> Modifier.background(fill)
                    glass -> Modifier
                        // bordered = false 只去掉那圈 1dp 描边，玻璃的高光保留
                        .liquidGlass(shape, tint = Paper.copy(alpha = 0.30f))
                        .then(if (bordered) Modifier.border(1.dp, Rule, shape) else Modifier)
                    bordered -> Modifier.border(1.dp, Rule, shape)
                    else -> Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun StatusChip(status: SessionStatus?) {
    val label = when (status) {
        SessionStatus.RUNNING -> "进行中"
        SessionStatus.PAUSED -> "已暂停"
        SessionStatus.ENDED -> "已结束"
        null -> "未开始"
    }
    val dot = when (status) {
        SessionStatus.RUNNING -> Vermilion
        else -> Muted.copy(alpha = 0.6f)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 12.sp, color = Muted, letterSpacing = 1.sp)
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(Rule.copy(alpha = 0.7f))
    )
}

val Transparent: Color = Color.Transparent
