package com.tengwear.tengstore.ui.logs

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/** 日志反馈页：查看日志、描述情况、勾选确认、复制/邮箱发送。 */
@Composable
fun LogsScreen(navController: NavHostController) {
    val viewModel: LogsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    val descLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> parseTextInputResult(result.data)?.let { viewModel.setDescription(it) } }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("日志反馈") } }

            item {
                GrayButton(
                    onClick = { launchTextInputSafely(descLauncher, "描述遇到的情况") },
                    label = { Text("描述情况") },
                    secondaryLabel = {
                        Text(
                            uiState.description.ifBlank { "未填写（发送前必填）" },
                            maxLines = 2,
                        )
                    },
                )
            }

            item {
                SwitchButton(
                    checked = uiState.confirmed,
                    onCheckedChange = { viewModel.setConfirmed(it) },
                    label = { Text("我已确认情况属实") },
                )
            }

            item {
                GrayButton(
                    onClick = { viewModel.copyToClipboard(context) },
                    label = { Text("复制到剪贴板") },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                )
            }

            item {
                GrayButton(
                    onClick = { viewModel.sendByEmail(context) },
                    label = { Text("邮箱发送日志") },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                )
            }

            uiState.notice?.let { notice ->
                item { Text(notice, modifier = Modifier.padding(8.dp)) }
            }

            item { ListHeader { Text("最近日志") } }
            if (uiState.entries.isEmpty()) {
                item { Text("暂无日志。", modifier = Modifier.padding(8.dp)) }
            } else {
                val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
                items(uiState.entries.size) { i ->
                    val e = uiState.entries[uiState.entries.size - 1 - i]
                    Text(
                        "${timeFmt.format(Date(e.time))} [${e.tag}] ${e.message}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            item {
                GrayButton(
                    onClick = { viewModel.clearLogs() },
                    label = { Text("清空日志") },
                )
            }
        }
    }
}
