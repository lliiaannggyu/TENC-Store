package com.tengwear.tengstore.ui.update

import android.content.Context
import android.content.Intent
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import com.tengwear.tengstore.BuildConfig
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize
import java.io.File
import com.tengwear.tengstore.ui.util.edgeSpacers

/**
 * 应用更新页：手动检查更新源仓库的最新 Release，
 * 发现新版本时展示版本号 / 体积 / 发布说明，可下载安装。
 * 非强制：随时可滑出页面忽略，不影响应用其他功能。
 */
@Composable
fun UpdateScreen(navController: NavHostController) {
    val vm: UpdateViewModel = viewModel()
    val ui by vm.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("应用更新") } }

            item {
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            when (ui.phase) {
                UpdateViewModel.Phase.IDLE -> {
                    item {
                        Button(
                            onClick = { vm.check() },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            label = { Text("检查更新", maxLines = 1) },
                            icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            colors = ButtonDefaults.buttonColors(),
                        )
                    }
                }

                UpdateViewModel.Phase.CHECKING -> {
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(6.dp))
                            Text("正在检查…", maxLines = 1)
                        }
                    }
                }

                UpdateViewModel.Phase.UP_TO_DATE -> {
                    item {
                        Text(
                            "已是最新版本（${ui.latestVersion}）",
                            modifier = Modifier.padding(8.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    item { RetryChip(vm) }
                }

                UpdateViewModel.Phase.ERROR -> {
                    item {
                        Text(
                            "检查失败：${ui.error}",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    item { RetryChip(vm) }
                }

                UpdateViewModel.Phase.AVAILABLE -> {
                    item {
                        Text(
                            "发现新版本 ${ui.latestVersion}",
                            modifier = Modifier.padding(8.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    ui.asset?.let { asset ->
                        item {
                            Text(
                                "${formatSize(asset.size)} · ${asset.name}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 2,
                            )
                        }
                    }
                    if (ui.releaseNotes.isNotBlank()) {
                        item {
                            Text(
                                ui.releaseNotes,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    val file = ui.downloadedFile
                    when {
                        // 下载中：条形进度
                        ui.downloadProgress != null -> {
                            val progress = ui.downloadProgress ?: 0
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                                    LinearBar(progress / 100f)
                                    Spacer(Modifier.height(4.dp))
                                    Text("下载中 $progress%", maxLines = 1)
                                }
                            }
                        }
                        // 下载完成：安装
                        file != null -> {
                            item {
                                Button(
                                    onClick = { installApk(context, file) },
                                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                                    label = { Text("安装新版本", maxLines = 1) },
                                    secondaryLabel = { Text(formatSize(file.length()), maxLines = 1) },
                                    icon = { Icon(Icons.Default.Android, contentDescription = null) },
                                    colors = ButtonDefaults.buttonColors(),
                                )
                            }
                        }
                        // 待下载
                        else -> {
                            item {
                                Button(
                                    onClick = { vm.download() },
                                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                                    label = { Text("下载更新", maxLines = 1) },
                                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                    colors = ButtonDefaults.buttonColors(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryChip(vm: UpdateViewModel) {
    Button(
        onClick = { vm.check() },
        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
        label = { Text("重新检查", maxLines = 1) },
        icon = { Icon(Icons.Default.Done, contentDescription = null) },
    )
}

/** 条形进度条（与「我的下载」页一致的自绘实现）。 */
@Composable
private fun LinearBar(progress: Float) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF8AB4F8)),
        )
    }
}

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
