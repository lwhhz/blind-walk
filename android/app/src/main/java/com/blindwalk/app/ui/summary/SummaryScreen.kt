package com.blindwalk.app.ui.summary

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blindwalk.app.BlindWalkApp
import com.blindwalk.app.card.CardExporter
import com.blindwalk.app.card.TripCardRenderer
import com.blindwalk.app.core.Fmt
import com.blindwalk.app.domain.model.SessionRecord
import com.blindwalk.app.domain.model.TITLE_MAX_LENGTH
import com.blindwalk.app.domain.model.TrackPoint
import com.blindwalk.app.ui.components.BwDialog
import com.blindwalk.app.ui.components.BwDialogActions
import com.blindwalk.app.ui.components.BwDialogTitle
import com.blindwalk.app.ui.components.BwSnackbarHost
import com.blindwalk.app.ui.components.BwSnackbarState
import com.blindwalk.app.ui.components.PillButton
import com.blindwalk.app.ui.theme.Ink
import com.blindwalk.app.ui.theme.Muted
import com.blindwalk.app.ui.theme.Paper
import com.blindwalk.app.ui.theme.Rule
import com.blindwalk.app.ui.theme.SerifFamily
import com.blindwalk.app.ui.theme.Vermilion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SummaryScreen(sessionId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as BlindWalkApp
    val scope = rememberCoroutineScope()

    var record by remember { mutableStateOf<SessionRecord?>(null) }
    var points by remember { mutableStateOf<List<TrackPoint>>(emptyList()) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    // 卡片默认明暗跟随当前纸色：选了墨纸就默认出深色卡片，不用每次手动切。
    // LocalIsDark 必须在组合里读，不能塞进 remember 的 lambda。
    val themeIsDark = com.blindwalk.app.ui.theme.LocalIsDark.current
    var cardDark by remember { mutableStateOf(themeIsDark) }
    var savedUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val snackbar = remember { BwSnackbarState() }

    LaunchedEffect(sessionId) {
        val payload = withContext(Dispatchers.IO) {
            val rec = app.container.repository.load(sessionId)
            val pts = app.container.repository.loadPoints(sessionId)
            rec to pts
        }
        record = payload.first
        points = payload.second
        payload.first?.let { rec ->
            card = withContext(Dispatchers.Default) {
                TripCardRenderer.render(context, rec, payload.second, cardDark)
            }
        }
    }

    // 切换浅色/深色卡片时重新渲染（并让之前的相册副本失效）。
    LaunchedEffect(cardDark) {
        val rec = record ?: return@LaunchedEffect
        if (card == null) return@LaunchedEffect
        card = withContext(Dispatchers.Default) {
            TripCardRenderer.render(context, rec, points, cardDark)
        }
        savedUri = null
    }

    fun saveCard(shareAfter: Boolean) {
        val bitmap = card ?: return
        val rec = record ?: return
        if (busy) return
        busy = true
        scope.launch {
            val result = runCatching {
                savedUri ?: CardExporter.saveToGallery(
                    context,
                    bitmap,
                    "blindwalk_${Fmt.fileStamp(rec.startedAt)}.png"
                ).also { savedUri = it }
            }
            busy = false
            result.onSuccess { uri ->
                if (shareAfter) {
                    CardExporter.share(context, uri, "我的瞎走记录 · ${Fmt.date(rec.startedAt)}")
                } else {
                    snackbar.show(scope, "已保存到相册 Pictures/BlindWalk")
                }
            }.onFailure {
                snackbar.show(scope, "保存失败：${it.message}")
            }
        }
    }

    fun applyRename(raw: String) {
        val rec = record ?: return
        val updated = rec.copy(title = raw.trim().ifBlank { null })
        record = updated
        showRename = false
        scope.launch {
            withContext(Dispatchers.IO) { app.container.repository.save(updated) }
            val pts = points
            card = withContext(Dispatchers.Default) {
                TripCardRenderer.render(context, updated, pts, cardDark)
            }
            // 标题变了，之前存到相册的那张已经过时，下次保存重新生成。
            savedUri = null
        }
    }

    // 置顶入口已统一收进首页的右滑手势，这里不再提供第二个入口。

    val solidPaper = !com.blindwalk.app.ui.glass.LocalGlassEnabled.current
    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (solidPaper) Modifier.background(com.blindwalk.app.ui.theme.BWTheme.colors.paper) else Modifier
            )
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(18.dp))

        Text(
            "这次走完了",
            fontFamily = SerifFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Normal,
            color = Ink,
            letterSpacing = 2.sp
        )

        Spacer(Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                record?.let { "${Fmt.date(it.startedAt)} ${Fmt.weekday(it.startedAt)}" } ?: "",
                fontSize = 12.sp,
                color = Muted,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.width(10.dp))
            SmallChip(
                text = if (record?.displayTitleOrNull() != null) "改标题" else "加标题",
                enabled = record != null
            ) { showRename = true }
            Spacer(Modifier.width(6.dp))
            SmallChip(
                text = if (cardDark) "深色卡片" else "浅色卡片",
                enabled = card != null
            ) { cardDark = !cardDark }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            val bitmap = card
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "行程总结卡片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Paper),
                    contentAlignment = Alignment.Center
                ) {
                    Text("正在生成卡片…", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                text = "保存图片",
                onClick = { saveCard(shareAfter = false) },
                modifier = Modifier.weight(1f),
                filled = false,
                enabled = card != null && !busy
            )
            PillButton(
                text = "分享",
                onClick = { saveCard(shareAfter = true) },
                modifier = Modifier.weight(1f),
                enabled = card != null && !busy
            )
        }

        Spacer(Modifier.height(10.dp))
        PillButton(
            text = "完成",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            filled = false
        )
        Spacer(Modifier.height(20.dp))
    }

        BwSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        )
    }

    if (showRename) {
        var draft by remember(record?.id) { mutableStateOf(record?.displayTitleOrNull().orEmpty()) }
        BwDialog(onDismissRequest = { showRename = false }) {
            BwDialogTitle("给这次瞎走起个名字")
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= TITLE_MAX_LENGTH) draft = it },
                singleLine = true,
                label = { Text("标题") },
                placeholder = { Text("例如：周五晚上的漫无目的") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink,
                    unfocusedBorderColor = Rule,
                    cursorColor = Ink,
                    focusedLabelColor = Ink,
                    unfocusedLabelColor = Muted,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                    focusedPlaceholderColor = Muted.copy(alpha = 0.75f),
                    unfocusedPlaceholderColor = Muted.copy(alpha = 0.75f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${draft.length} / $TITLE_MAX_LENGTH　留空则显示默认标题",
                fontSize = 11.sp,
                color = Muted
            )
            Spacer(Modifier.height(12.dp))
            BwDialogActions(
                confirmLabel = "保存",
                onCancel = { showRename = false },
                onConfirm = { applyRename(draft) }
            )
        }
    }
}

@Composable
private fun SmallChip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = if (enabled) Vermilion else Muted)
    }
}
