package com.blindwalk.app.ui.trash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.ui.components.BwDialog
import com.blindwalk.app.ui.components.BwDialogActions
import com.blindwalk.app.ui.components.BwDialogTitle
import com.blindwalk.app.ui.components.PillButton
import com.blindwalk.app.ui.theme.BWTheme
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.SerifFamily

@Composable
fun TrashScreen(onDone: () -> Unit) {
    val vm: TrashViewModel = viewModel()
    val items by vm.items.collectAsStateWithLifecycle()
    val sizeBytes by vm.sizeBytes.collectAsStateWithLifecycle()

    var purgeTarget by remember { mutableStateOf<SessionRecord?>(null) }
    var showPurgeAll by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    // 玻璃开启时这一页透出壁纸（玻璃控件采样得到），关闭时退回纯纸底
    val solidPaper = !com.blindwalk.app.ui.glass.LocalGlassEnabled.current
    Column(
        Modifier
            .fillMaxSize()
            .then(
                if (solidPaper) Modifier.background(BWTheme.colors.paper) else Modifier
            )
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("回收站", fontFamily = SerifFamily, fontSize = 30.sp, fontWeight = FontWeight.Normal, letterSpacing = 4.sp, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "不限时保留，只有你点「彻底删除」才会真的消失",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            TextButton(onClick = onDone) { Text("完成", fontSize = 14.sp) }
        }

        Spacer(Modifier.height(18.dp))

        if (items.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "回收站是空的\n删除的行程会先来到这里",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 26.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { record ->
                    TrashRowItem(
                        record = record,
                        onRestore = { vm.restore(record.id) },
                        onPurge = { purgeTarget = record }
                    )
                }
            }
        }

        if (items.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "共 ${items.size} 项 · ${formatBytes(sizeBytes)}",
                fontSize = 12.sp,
                color = Muted
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = "清空回收站",
                onClick = { showPurgeAll = true },
                modifier = Modifier.fillMaxWidth(),
                filled = false
            )
        }

        Spacer(Modifier.height(20.dp))
    }

    purgeTarget?.let { target ->
        BwDialog(onDismissRequest = { purgeTarget = null }) {
            BwDialogTitle("彻底删除？")
            Spacer(Modifier.height(10.dp))
            Text(
                "「${target.displayTitleOrNull() ?: Fmt.date(target.startedAt)}」的轨迹将被永久销毁，无法恢复。",
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            BwDialogActions(
                confirmLabel = "彻底删除",
                confirmDestructive = true,
                onCancel = { purgeTarget = null },
                onConfirm = {
                    vm.purge(target.id)
                    purgeTarget = null
                }
            )
        }
    }

    if (showPurgeAll) {
        BwDialog(onDismissRequest = { showPurgeAll = false }) {
            BwDialogTitle("清空回收站？")
            Spacer(Modifier.height(10.dp))
            Text(
                "${items.size} 条记录的轨迹将被永久销毁，无法恢复。",
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            BwDialogActions(
                confirmLabel = "清空",
                confirmDestructive = true,
                onCancel = { showPurgeAll = false },
                onConfirm = {
                    vm.purgeAll()
                    showPurgeAll = false
                }
            )
        }
    }
}

@Composable
private fun TrashRowItem(
    record: SessionRecord,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BWTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        val title = record.displayTitleOrNull()
        Text(
            title ?: "${Fmt.date(record.startedAt)} ${Fmt.weekday(record.startedAt)}",
            fontSize = 15.sp,
            color = Ink,
            fontWeight = if (title != null) FontWeight.Medium else FontWeight.Normal
        )
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                if (title != null) append("${Fmt.date(record.startedAt)} · ")
                append(Fmt.distance(record.distanceMeters))
                append(" · ${record.instructionCount} 次")
                record.deletedAt?.let { append(" · 删除于 ${Fmt.time(it)}") }
            },
            fontSize = 12.sp,
            color = Muted
        )
        Spacer(Modifier.height(10.dp))
        Row {
            TextButton(onClick = onRestore, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("恢复", fontSize = 13.sp, color = Ink)
            }
            Spacer(Modifier.width(16.dp))
            TextButton(onClick = onPurge, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("彻底删除", fontSize = 13.sp, color = Muted)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb < 1024) String.format(java.util.Locale.US, "%.0f KB", kb)
    else String.format(java.util.Locale.US, "%.1f MB", kb / 1024)
}
