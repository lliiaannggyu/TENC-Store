package com.tengwear.tengstore.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import com.tengwear.tengstore.data.download.DownloadTracker

/** 条形进度条（自绘，圆角胶囊样式）。 */
@Composable
fun LinearBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f))
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(scheme.outline.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(CircleShape)
                .background(scheme.primary),
        )
    }
}

/**
 * 首页下载状态条（可视化样式）：
 * - 有下载任务时：胶囊条内显示文件名 + 百分比 + 条形进度；
 * - 刚下载结束时：成功/失败提示胶囊，点按关闭，超时自动消失。
 */
@Composable
fun DownloadStatusBar(
    active: DownloadTracker.Active?,
    finished: DownloadTracker.Finished?,
    onFinishedDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(50)
        when {
            active != null -> Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(shape)
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        active.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp).weight(1f),
                    )
                    Text(
                        "${active.progress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                LinearBar(progress = active.progress / 100f)
            }
            finished != null -> Row(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(shape)
                    .background(
                        if (finished.success) Color(0xFF1E3A2F) else Color(0xFF3A1E1E),
                    )
                    .clickable(onClick = onFinishedDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (finished.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (finished.success) Color(0xFF6FCF97) else Color(0xFFEB7A7A),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    (if (finished.success) "下载完成 · " else "下载失败 · ") + finished.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).weight(1f),
                )
            }
        }
    }
}
