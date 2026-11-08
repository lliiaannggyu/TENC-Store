package com.tengwear.tengstore.ui.inbox

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.AlertDialog
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/** 信息板块：只显示通过日志口令校验的邮件（解密时间与接收时间误差 ≤5 分钟）。 */
@Composable
fun InboxScreen(navController: NavHostController) {
    val viewModel: InboxViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var detail by remember { mutableStateOf<InboxItem?>(null) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    LaunchedEffect(Unit) { viewModel.refresh() }

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
                                if (item.type != null) append(" · ${item.type}")
                                if (item.project != null) append(" · ${item.project.substringAfter("/")}")
                            },
                            maxLines = 1,
                        )
                    },
                    secondaryLabel = {
                        Text(item.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "（日志）", maxLines = 1)
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
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
            title = { Text("信息详情", maxLines = 1) },
            text = {
                Column {
                    Text(
                        buildString {
                            appendLine("发件人 ${item.from}")
                            appendLine("接收 ${timeFmt.format(Date(item.receivedAt))} · 发送 ${timeFmt.format(Date(item.sentAt))}")
                            item.project?.let { appendLine("项目 $it") }
                            item.type?.let { appendLine("类型 $it") }
                            item.sender?.let { appendLine("反馈人 $it") }
                        },
                    )
                    Text(
                        item.body.ifBlank { "（正文为空）" },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            },
        )
    }
}
