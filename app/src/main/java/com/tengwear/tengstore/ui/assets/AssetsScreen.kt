package com.tengwear.tengstore.ui.assets

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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.items
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Asset
import com.tengwear.tengstore.ui.util.formatSize

@Composable
fun AssetsScreen(navController: NavHostController, releaseId: Long) {
    val release = AppContainer.releaseCache[releaseId]
    val viewModel: DownloadViewModel = viewModel()
    val dlState by viewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    val apks = remember(release) { release?.assets?.filter { it.isApk } ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = {
                if (release != null) PositionIndicator(scalingLazyListState = listState)
            },
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
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
    Chip(
        onClick = onDownload,
        label = { Text(asset.name, maxLines = 2) },
        secondaryLabel = { Text(formatSize(asset.size), maxLines = 1) },
        icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
    )
}
