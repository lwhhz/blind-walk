package com.blindwalk.app.ui.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.ui.components.PillButton
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.SerifFamily

/**
 * 「瞎走」tab 的准备页：还没开始行程时看到的那一屏。
 *
 * 独立成一页而不是直接复用行程页，是因为行程页的所有数字都在等一个真实会话；
 * 没有会话时硬塞进去只会显示一排 0，像坏了。
 */
@Composable
fun TripIdleScreen(onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "瞎走",
                fontFamily = SerifFamily,
                fontSize = 44.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 8.sp,
                color = Ink
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "把方向盘交给随机，\n走一条事先不知道去哪的路。",
                fontSize = 14.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.5.sp,
                color = Muted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(48.dp))
            PillButton(
                text = "开始瞎走",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                filled = true
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "开始后切到别的页面也会继续记录",
                fontSize = 11.sp,
                color = Muted
            )
        }
    }
}
