package com.tengwear.tengstore.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Asset
import com.tengwear.tengstore.ui.util.GrayButton
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.formatSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Smartphone
import kotlinx.coroutines.launch

/**
 * 项目下载页：左右两页切换。
 * 页1「APK 文件」：该版本全部 .apk 产物，下载后可直接安装；
 * 页2「其他文件」：该版本其余产物（签名、说明、数据文件等），仅下载保存。
 * 顶部两个按钮用于快速跳页，也可左右滑动切换。
 */
@Composable
fun AssetsScreen(navController: NavHostController, releaseId: Long) {
    val release = AppContainer.releaseCache[releaseId]
    val viewModel: DownloadViewModel = viewModel()
    val dlState by viewModel.state.collectAsState()
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    val apks = remember(release) { release?.assets?.filter { it.isApk } ?: emptyList() }
    val others = remember(release) { release?.assets?.filterNot { it.isApk } ?: emptyList() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val isApkPage = page == 0
                val listState = rememberScalingLazyListState()
                RotaryScroll(listState)
                val group = if (isApkPage) apks else others

                ScalingLazyColumn(
                    state = listState,
                    autoCentering = null,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    edgeSpacers()
                    item { ListHeader { Text(if (isApkPage) "APK 文件" else "其他文件") } }

                    // 页面切换按钮：当前页主题色高亮，另一页灰色
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            PagerTab(
                                text = "APK",
                                selected = isApkPage,
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { pagerState.scrollToPage(0) } },
                            )
                            PagerTab(
                                text = "其他",
                                selected = !isApkPage,
                                modifier = Modifier.weight(1f),
                                onClick = { scope.launch { pagerState.scrollToPage(1) } },
                            )
                        }
                    }

                    if (release == null) {
                        item { Text("未找到该版本。", modifier = Modifier.padding(8.dp)) }
                    } else if (group.isEmpty()) {
                        item {
                            Text(
                                if (isApkPage) "该版本没有 APK 产物。" else "该版本没有其他文件。",
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        items(group.size) { i ->
                            AssetChip(
                                asset = group[i],
                                onDownload = { viewModel.download(group[i]) },
                            )
                        }
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
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** 双页切换小按钮：选中态为主题色按钮，未选态为灰色按钮。 */
@Composable
private fun PagerTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(LIST_CHIP_HEIGHT),
            icon = {
                Icon(
                    if (text == "APK") Icons.Default.Smartphone else Icons.Default.Description,
                    contentDescription = null,
                )
            },
            label = { Text(text, maxLines = 1) },
        )
    } else {
        GrayButton(
            onClick = onClick,
            modifier = modifier.height(LIST_CHIP_HEIGHT),
            icon = {
                Icon(
                    if (text == "APK") Icons.Default.Smartphone else Icons.Default.Description,
                    contentDescription = null,
                )
            },
            label = { Text(text, maxLines = 1) },
        )
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
