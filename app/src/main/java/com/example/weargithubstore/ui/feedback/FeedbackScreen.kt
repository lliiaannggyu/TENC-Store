package com.example.weargithubstore.ui.feedback

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme

import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.collectAsState
import com.example.weargithubstore.ui.util.LIST_CHIP_HEIGHT
import com.example.weargithubstore.ui.util.launchTextInputSafely
import com.example.weargithubstore.ui.util.parseTextInputResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/** 项目反馈页：选择建议 / Bug，填写内容，发到项目开发者邮箱（需已绑定邮箱）。 */
@Composable
fun FeedbackScreen(
    navController: NavHostController,
    fullName: String,
    devEmail: String,
) {
    val viewModel: FeedbackViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context: Context = LocalContext.current
    val listState = rememberScalingLazyListState()

    LaunchedEffect(fullName, devEmail) { viewModel.init(fullName, devEmail) }

    val contentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        parseTextInputResult(result.data)?.let { viewModel.setContent(it) }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("反馈 · ${fullName.substringAfter("/")}"); } }

            item {
                Text(
                    if (devEmail.isBlank()) {
                        "该项目未附带开发者邮箱，无法反馈。"
                    } else {
                        "反馈将发送给项目开发者：$devEmail"
                    },
                    modifier = Modifier.padding(8.dp),
                )
            }

            item { ListHeader { Text("反馈类型") } }
            item {
                Chip(
                    onClick = { viewModel.setType(FeedbackType.SUGGESTION) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("建议", maxLines = 1) },
                    secondaryLabel = { Text(if (uiState.type == FeedbackType.SUGGESTION) "已选择" else "", maxLines = 1) },
                    icon = { Icon(Icons.Default.Lightbulb, contentDescription = null) },
                    colors = if (uiState.type == FeedbackType.SUGGESTION) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                )
            }
            item {
                Chip(
                    onClick = { viewModel.setType(FeedbackType.BUG) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("Bug", maxLines = 1) },
                    secondaryLabel = { Text(if (uiState.type == FeedbackType.BUG) "已选择" else "", maxLines = 1) },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    colors = if (uiState.type == FeedbackType.BUG) {
                        ChipDefaults.primaryChipColors()
                    } else {
                        ChipDefaults.secondaryChipColors()
                    },
                )
            }

            item { ListHeader { Text("反馈内容") } }
            item {
                Chip(
                    onClick = {
                        launchTextInputSafely(contentLauncher, "输入反馈内容")
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("填写内容", maxLines = 1) },
                    secondaryLabel = {
                        Text(uiState.content.ifBlank { "未填写，点击输入" }, maxLines = 1)
                    },
                )
            }

            item {
                Chip(
                    onClick = { viewModel.send(context) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = !uiState.busy && devEmail.isNotBlank(),
                    label = { Text("发送反馈", maxLines = 1) },
                    icon = { Icon(Icons.Default.Send, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
        }
    }

    // 发送提醒弹窗：发送中转圈，完成后显示结果，点确定关闭
    if (uiState.busy || uiState.notice != null) {
        val chipsColor = ChipDefaults.primaryChipColors()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = !uiState.busy,
                    onClick = { viewModel.dismissDialog() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                if (uiState.busy) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在发送反馈…")
                    Text(
                        "通过绑定邮箱发送到开发者",
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        uiState.notice ?: "",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Chip(
                        onClick = { viewModel.dismissDialog() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("确定", maxLines = 1) },
                        colors = chipsColor,
                    )
                }
            }
        }
    }
}
