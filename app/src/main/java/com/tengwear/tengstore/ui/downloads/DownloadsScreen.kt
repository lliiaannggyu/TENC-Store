package com.tengwear.tengstore.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.download.ApkDownloader
import com.tengwear.tengstore.data.download.DownloadTracker
import com.tengwear.tengstore.ui.util.formatSize
import java.io.File
import com.tengwear.tengstore.ui.util.edgeSpacers

@Composable
fun DownloadsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val downloader = remember { ApkDownloader(context) }
    var files by remember { mutableStateOf(downloader.listDownloadedApks()) }
    val active by DownloadTracker.active.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    // 下载进度变化 / 完成时刷新文件列表（完成后新文件即出现在列表中）
    androidx.compose.runtime.LaunchedEffect(active) {
        files = downloader.listDownloadedApks()
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("我的下载") } }

            // 下载中：条形进度条
            active?.let { a ->
                item { ListHeader { Text("下载中") } }
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                        Text(a.name, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        LinearBar(progress = a.progress / 100f)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("${a.progress}%")
                                if (a.total > 0) {
                                    append(" · ${formatSize(a.soFar)} / ${formatSize(a.total)}")
                                }
                            },
                            maxLines = 1,
                        )
                    }
                }
            }

            if (files.isEmpty()) {
                if (active == null) {
                    item {
                        Text("还没有下载过 APK。", modifier = Modifier.padding(8.dp))
                    }
                }
            } else {
                item { ListHeader { Text("已完成") } }
                items(files.size) { i ->
                    val file = files[i]
                    DownloadedChip(
                        file = file,
                        onInstall = { installApk(context, file) },
                        onDelete = {
                            if (downloader.delete(file)) {
                                files = downloader.listDownloadedApks()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 条形进度条（Wear Material 1.3 未提供，自绘实现）。 */
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

@Composable
private fun DownloadedChip(file: File, onInstall: () -> Unit, onDelete: () -> Unit) {
    Button(
        onClick = onInstall,
        label = { Text(file.name, maxLines = 2) },
        secondaryLabel = { Text("点按安装 · ${formatSize(file.length())}", maxLines = 1) },
        icon = { Icon(Icons.Default.Android, contentDescription = null) },
    )
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
