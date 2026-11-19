package com.tengwear.tengstore.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.download.ApkDownloader
import com.tengwear.tengstore.data.download.DownloadQueue
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.LinearBar
import com.tengwear.tengstore.ui.util.formatSize
import java.io.File
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT

/** 我的下载：多任务队列（多项目多文件）+ 全部已下载文件（APK 可安装）。 */
@Composable
fun DownloadsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val downloader = remember { ApkDownloader(context) }
    var files by remember { mutableStateOf(downloader.listDownloadedFiles()) }
    val queue by DownloadQueue.tasks.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    androidx.compose.runtime.LaunchedEffect(queue.size) {
        files = downloader.listDownloadedFiles()
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = null,
            modifier = Modifier.fillMaxSize(),
        ) {
            edgeSpacers()
            item { ListHeader { Text("我的下载") } }

            // 多任务队列：每个文件独立进度，可取消 / 重试
            val active = queue.filter {
                it.state == DownloadQueue.State.QUEUED ||
                    it.state == DownloadQueue.State.DOWNLOADING ||
                    it.state == DownloadQueue.State.FAILED ||
                    it.state == DownloadQueue.State.CANCELLED
            }
            if (active.isNotEmpty()) {
                item { ListHeader { Text("下载队列（${active.size}）") } }
                active.forEach { task ->
                    item(key = "task_${task.id}") {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                            Text(
                                when (task.state) {
                                    DownloadQueue.State.QUEUED -> "排队：${task.label}"
                                    DownloadQueue.State.DOWNLOADING -> "下载中：${task.label}"
                                    DownloadQueue.State.FAILED -> "失败：${task.label}"
                                    DownloadQueue.State.CANCELLED -> "已取消：${task.label}"
                                    else -> task.label
                                },
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearBar(progress = task.progress / 100f)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                buildString {
                                    append("${task.progress}%")
                                    if (task.total > 0) {
                                        append(" · ${formatSize(task.soFar)} / ${formatSize(task.total)}")
                                    }
                                    task.error?.let { append(" · $it") }
                                },
                                maxLines = 2,
                            )
                        }
                    }
                    item(key = "task_action_${task.id}") {
                        GrayButton(
                            onClick = {
                                when (task.state) {
                                    DownloadQueue.State.QUEUED,
                                    DownloadQueue.State.DOWNLOADING -> DownloadQueue.cancel(task.id)
                                    DownloadQueue.State.FAILED,
                                    DownloadQueue.State.CANCELLED -> DownloadQueue.retry(task.id)
                                    else -> Unit
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            icon = {
                                Icon(
                                    if (task.state == DownloadQueue.State.FAILED ||
                                        task.state == DownloadQueue.State.CANCELLED
                                    ) Icons.Default.Refresh else Icons.Default.Delete,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    when (task.state) {
                                        DownloadQueue.State.FAILED,
                                        DownloadQueue.State.CANCELLED -> "重试"
                                        else -> "取消"
                                    },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            if (queue.isNotEmpty()) {
                item {
                    GrayButton(
                        onClick = { DownloadQueue.clearFinished() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("清除已完成记录", maxLines = 1) },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    )
                }
            }

            if (files.isEmpty()) {
                if (queue.isEmpty()) {
                    item {
                        Text(
                            "还没有下载过文件。到项目版本页点「下载本页全部」可批量下载。",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            } else {
                item { ListHeader { Text("已下载（${files.size}）") } }
                items(files.size) { i ->
                    val file = files[i]
                    val isApk = file.name.endsWith(".apk", ignoreCase = true)
                    DownloadedChip(
                        file = file,
                        onInstall = { if (isApk) installApk(context, file) },
                        onDelete = {
                            if (downloader.delete(file)) {
                                files = downloader.listDownloadedFiles()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedChip(file: File, onInstall: () -> Unit, onDelete: () -> Unit) {
    GrayButton(
        onClick = onInstall,
        enabled = file.name.endsWith(".apk", ignoreCase = true),
        label = { Text(file.name, maxLines = 2) },
        secondaryLabel = {
            Text("${formatSize(file.length())} · ${file.parentFile?.name.orEmpty()}", maxLines = 1)
        },
        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
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
