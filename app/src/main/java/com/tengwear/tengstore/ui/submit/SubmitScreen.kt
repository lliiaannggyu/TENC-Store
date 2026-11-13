package com.tengwear.tengstore.ui.submit

import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MarkEmailRead
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/**
 * 应用投稿页：表格式表单——
 * 「GitHub 链接」「一句话描述」两项点按输入；投稿邮箱自动使用已绑定的开发者邮箱（只读展示）。
 * 点「发送投稿」后按指定格式 + 口令发到投稿收件邮箱，由开发者审核收录 / 打回。
 */
@Composable
fun SubmitScreen(navController: NavHostController) {
    val viewModel: SubmitViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.init() }

    var pendingField by rememberSaveable { mutableStateOf("") }
    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val value = parseTextInputResult(result.data) ?: return@rememberLauncherForActivityResult
        when (pendingField) {
            "link" -> viewModel.setLink(value)
            "desc" -> viewModel.setDesc(value)
        }
    }

    fun ask(field: String, label: String, prefill: String? = null) {
        pendingField = field
        launchTextInputSafely(inputLauncher, label, prefill)
    }

    val scheme = MaterialTheme.colorScheme
    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
            edgeSpacers()
            item { ListHeader { Text("应用投稿") } }

            item {
                Text(
                    "你的应用想进测试列表？填 GitHub 链接发给我们，审核通过即收录，结果以邮件通知。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // 表格式表单卡片
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerLow)
                        .padding(vertical = 6.dp),
                ) {
                    FormFieldRow(
                        label = "GitHub 链接",
                        value = uiState.link,
                        hint = "点按输入仓库链接",
                        icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    ) { ask("link", "输入 GitHub 仓库链接", uiState.link) }
                    Divider()
                    FormFieldRow(
                        label = "一句话描述",
                        value = uiState.desc,
                        hint = "点按输入应用功能",
                        icon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    ) { ask("desc", "一句话描述应用功能", uiState.desc) }
                    Divider()
                    // 投稿邮箱：自动填写，只读展示
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "投稿邮箱",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Text(
                            uiState.email.ifBlank { "未绑定（到设置绑定）" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.email.isBlank()) scheme.onSurfaceVariant else scheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                    }
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable(enabled = !uiState.busy) { viewModel.send() }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (uiState.busy) "正在发送…" else "发送投稿",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    when {
                        uiState.targetEmail == null -> "投稿通道状态：未就绪（检查网络后重试）"
                        else -> "投稿通道状态：已就绪"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }

    // 结果弹窗
    uiState.notice?.let { notice ->
        AlertDialog(
            visible = true,
            onDismissRequest = { viewModel.dismissNotice() },
            confirmButton = {
                GrayButton(onClick = { viewModel.dismissNotice() }, label = { Text("好的") })
            },
            title = { Text(if (uiState.sent) "投稿成功" else "投稿未发出", maxLines = 1) },
            text = { Text(notice) },
        )
    }
}

/** 表格输入行：左侧字段名 + 图标，右侧当前值或提示，点按进入输入。 */
@Composable
private fun FormFieldRow(
    label: String,
    value: String,
    hint: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            value.ifBlank { hint },
            style = MaterialTheme.typography.labelSmall,
            color = if (value.isBlank()) scheme.onSurfaceVariant else scheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
    }
}

/** 表格行之间的分隔线。 */
@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}
