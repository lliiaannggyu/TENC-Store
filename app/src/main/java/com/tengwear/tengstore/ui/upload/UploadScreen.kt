package com.tengwear.tengstore.ui.upload

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import java.io.File

/**
 * 应用上传页（新流程）：
 * 检查令牌 → 选择自己的仓库 → 内置文件管理器选 APK → 填说明 → 更新。
 */
@Composable
fun UploadScreen(navController: NavHostController) {
    val viewModel: UploadViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.onEnter() }

    // 接收文件管理器回传的 APK 路径
    val pickedPath by navController.currentBackStackEntry
        ?.savedStateHandle?.getStateFlow("picked_apk", "")?.collectAsState() ?: return
    LaunchedEffect(pickedPath) {
        if (pickedPath.isNotBlank()) {
            viewModel.selectFile(File(pickedPath))
            navController.currentBackStackEntry?.savedStateHandle?.set("picked_apk", "")
        }
    }

    val noteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> parseTextInputResult(result.data)?.let { viewModel.setNote(it) } }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("应用上传") } }

            if (!uiState.tokenReady) {
                // 第一步：检查有无授予令牌
                item {
                    Text(
                        "未检测到 GitHub 令牌，无法获取你的仓库列表。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
                item {
                    Button(
                        onClick = { navController.navigate("settings") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("去设置授予令牌", maxLines = 1) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        colors = ButtonDefaults.buttonColors(),
                    )
                }
                return@ScalingLazyColumn
            }

            item { ListHeader { Text("选择仓库") } }
            when {
                uiState.reposLoading -> {
                    item {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            label = { Text("仓库列表加载中…") },
                        )
                    }
                }
                uiState.reposError != null -> {
                    item { Text(uiState.reposError ?: "", modifier = Modifier.padding(8.dp)) }
                    item {
                        Button(
                            onClick = { viewModel.loadRepos() },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            label = { Text("重试", maxLines = 1) },
                            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        )
                    }
                }
                else -> {
                    items(uiState.repos.size) { i ->
                        val repo = uiState.repos[i]
                        val selected = uiState.selectedRepo == repo
                        Button(
                            onClick = { viewModel.selectRepo(repo) },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            label = { Text(repo, maxLines = 1) },
                            secondaryLabel = { Text(if (selected) "已选择" else "", maxLines = 1) },
                            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            colors = if (selected) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                        )
                    }
                }
            }

            item { ListHeader { Text("选择 APK") } }
            item {
                Button(
                    onClick = { navController.navigate("filemanager") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("文件管理器", maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            uiState.pickedFile?.let { "${it.name} · ${formatSize(it.length())}" }
                                ?: "未选择",
                            maxLines = 1,
                        )
                    },
                    icon = { Icon(Icons.Default.Android, contentDescription = null) },
                )
            }

            item { ListHeader { Text("发布说明") } }
            item {
                Button(
                    onClick = { launchTextInputSafely(noteLauncher, "输入更新说明") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("填写更新说明", maxLines = 1) },
                    secondaryLabel = {
                        Text(uiState.note.ifBlank { "未填写" }, maxLines = 1)
                    },
                )
            }
            item {
                SwitchButton(
                    checked = uiState.confirmed,
                    onCheckedChange = { viewModel.setConfirmed(it) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("确认按格式发布", maxLines = 1) },
                )
            }

            item {
                Button(
                    onClick = { viewModel.publish() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = !uiState.busy,
                    label = { Text(if (uiState.busy) "发布中…" else "更新所选仓库", maxLines = 1) },
                    icon = {
                        if (uiState.busy) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(),
                )
            }
            item {
                Button(
                    onClick = { viewModel.sendReportByEmail(context) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("邮箱托管发布记录", maxLines = 1) },
                    icon = { Icon(Icons.Default.Mail, contentDescription = null) },
                )
            }

            uiState.notice?.let { notice ->
                item { Text(notice, modifier = Modifier.padding(8.dp)) }
            }
        }
    }
}
