package com.tengwear.tengstore.ui.assets

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Asset
import com.tengwear.tengstore.ui.util.formatSize
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

@Composable
fun AssetsScreen(navController: NavHostController, releaseId: Long) {
    val release = AppContainer.releaseCache[releaseId]
    val viewModel: DownloadViewModel = viewModel()
    val dlState by viewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    val apks = remember(release) { release?.assets?.filter { it.isApk } ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenScaffold(scrollState = listState,             timeText = { TimeText() },
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
        edgeSpacers()
                item { ListHeader { Text("APK 产物") } }
                if (release == null) {
                    item {
                        Text("未找到该版本。", modifier = Modifier.padding(8.dp))
                    }
                } else {
                    if (apks.isEmpty()) {
                        item {
                            Text(
                                "该版本没有 APK 产物。",
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(apks.size) { i ->
                            val asset = apks[i]
                            AssetChip(asset = asset, onDownload = { viewModel.download(asset) })
                        }
                        // 下载进度统一在「我的下载」页以条形进度条展示
                        when (val s = dlState) {
                            is DownloadUiState.Downloading -> item {
                                Text(
                                    "正在下载 ${s.asset.name}，进度见「我的下载」",
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            is DownloadUiState.Failed -> item {
                                Text(
                                    s.message ?: "下载失败，可在我的下载重试",
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetChip(asset: Asset, onDownload: () -> Unit) {
    GrayButton(
        onClick = onDownload,
        label = { Text(asset.name, maxLines = 2) },
        secondaryLabel = { Text(formatSize(asset.size), maxLines = 1) },
        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
    )
}
