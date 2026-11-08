package com.tengwear.tengstore.ui.inbox

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.AlertDialog
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult

/**
 * 信息板块：日志 / 项目反馈 / 应用投稿 / 投稿结果四类邮件。
 * 投稿邮件可「确认收录」（自动追加测试索引）或「打回」（填理由后邮件通知投稿人）。
 */
@Composable
fun InboxScreen(navController: NavHostController) {
    val viewModel: InboxViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var detail by remember { mutableStateOf<InboxItem?>(null) }
    var rejectTarget by remember { mutableStateOf<InboxItem?>(null) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // 打回：填写理由后自动发投稿结果邮件
    val rejectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val reason = parseTextInputResult(result.data) ?: return@rememberLauncherForActivityResult
        rejectTarget?.let { viewModel.rejectSubmission(it, reason) }
        rejectTarget = null
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("信息") } }

            if (uiState.loading) {
                item { GrayButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("正在拉取信息…") }) }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            if (!uiState.loading && uiState.error == null && uiState.items.isEmpty()) {
                item {
                    Text(
                        "暂无信息。只有携带有效日志口令的邮件才会显示在这里。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            items(uiState.items.size) { i ->
                val item = uiState.items[i]
                GrayButton(
                    onClick = { detail = item },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = {
                        Text(
                            buildString {
                                append(timeFmt.format(Date(item.receivedAt)))
                                append(" · ${item.kind.label}")
                                when (item.kind) {
                                    InboxKind.SUBMISSION -> item.link?.let { append(" · ${it.substringAfter("/")}") }
                                    InboxKind.REVIEW -> item.project?.let { append(" · ${it.substringAfter("/")}") }
                                    else -> item.project?.let { append(" · ${it.substringAfter("/")}") }
                                }
                            },
                            maxLines = 1,
                        )
                    },
                    secondaryLabel = {
                        Text(
                            item.body.lineSequence().firstOrNull { it.isNotBlank() }
                                ?: item.reviewReason.orEmpty().ifBlank { "（正文为空）" },
                            maxLines = 1,
                        )
                    },
                    icon = {
                        Icon(
                            when (item.kind) {
                                InboxKind.SUBMISSION -> Icons.Default.CloudUpload
                                InboxKind.REVIEW -> Icons.Default.FactCheck
                                InboxKind.FEEDBACK -> Icons.Default.Email
                                InboxKind.LOG -> Icons.Default.Info
                            },
                            contentDescription = item.kind.label,
                        )
                    },
                )
            }

            item {
                GrayButton(
                    onClick = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = !uiState.loading,
                    label = { Text("刷新信息", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }

    detail?.let { item ->
        AlertDialog(
            visible = true,
            onDismissRequest = { detail = null },
            confirmButton = {},
            title = { Text("${item.kind.label}详情", maxLines = 1) },
            text = {
                Column {
                    Text(
                        buildString {
                            appendLine("发件人 ${item.from}")
                            appendLine("接收 ${timeFmt.format(Date(item.receivedAt))}")
                            item.project?.let { appendLine("项目 $it") }
                            item.type?.let { appendLine("类型 $it") }
                            item.sender?.let { appendLine("投稿人 $it") }
                            item.link?.let { appendLine("链接 $it") }
                            item.reviewResult?.let { appendLine("结果 $it") }
                            item.reviewReason?.let { appendLine("理由 $it") }
                        },
                    )
                    Text(
                        item.body.ifBlank { "（正文为空）" },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    if (item.kind == InboxKind.SUBMISSION) {
                        val handling = uiState.handling != null
                        GrayButton(
                            onClick = {
                                detail = null
                                viewModel.approveSubmission(item)
                            },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            enabled = !handling,
                            label = { Text("确认收录", maxLines = 1) },
                            icon = { Icon(Icons.Default.FactCheck, contentDescription = null) },
                        )
                        GrayButton(
                            onClick = {
                                detail = null
                                rejectTarget = item
                                launchTextInputSafely(rejectLauncher, "输入打回理由")
                            },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            enabled = !handling,
                            label = { Text("打回", maxLines = 1) },
                            icon = {
                                if (handling) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Icon(Icons.Default.Email, contentDescription = null)
                            },
                        )
                    }
                }
            },
        )
    }
}
