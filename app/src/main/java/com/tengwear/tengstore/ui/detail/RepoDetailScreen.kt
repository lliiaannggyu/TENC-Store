package com.tengwear.tengstore.ui.detail

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.wear.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/**
 * 仓库详情页：README 文字说明 + 配图、最新版本信息、下载与收藏。
 * 从搜索结果、收藏、历史记录进入。
 */
@Composable
fun RepoDetailScreen(navController: NavHostController, fullName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: RepoDetailViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val settings = AppContainer.settings
    val favorites by settings.favorites.collectAsState()
    val isFavorite = fullName in favorites
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(fullName) { viewModel.init(fullName) }

    // 快捷收录：静默校验当前令牌对测试应用仓库的权限，有权限才显示入口（不展示任何令牌/仓库信息）
    var canAddToTestHub by remember { mutableStateOf(false) }
    var addingToTestHub by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(fullName) {
        canAddToTestHub = com.tengwear.tengstore.ui.util.TestHubAdder.hasAccess() == true
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text(fullName.substringAfter("/")) } }

            item {
                Text(
                    fullName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item { com.tengwear.tengstore.ui.util.RepoNameTranslation(fullName) }

            val release = uiState.latest
            item {
                Text(
                    buildString {
                        if (uiState.loading) {
                            append("版本信息加载中…")
                        } else if (release == null) {
                            append(uiState.error ?: "暂无可用版本")
                        } else {
                            appendLine("最新版本：${release.tagName}")
                            val apks = release.apkAssets
                            if (apks.isNotEmpty()) {
                                appendLine("包名：${apks.first().name}")
                                appendLine("体积：${formatSize(apks.sumOf { it.size })}")
                            }
                            append("发布时间：${release.publishedAt ?: "未知"}")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item {
                GrayButton(
                    onClick = { navController.navigate("releases/${fullName.replace("/", "~~")}") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = release != null,
                    label = { Text("下载", maxLines = 1) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
            }
            item {
                GrayButton(
                    onClick = { settings.toggleFavorite(fullName) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text(if (isFavorite) "取消收藏" else "收藏", maxLines = 1) },
                    icon = {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    colors = if (isFavorite) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                )
            }

            item { ListHeader { Text("项目介绍") } }
            when {
                uiState.introLoading -> {
                    item {
                        Text(
                            "介绍加载中…",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                uiState.intro == null ||
                    (uiState.intro?.paragraphs?.isEmpty() ?: true &&
                        (uiState.intro?.images?.isEmpty() ?: true)) -> {
                    item {
                        Text(
                            "该仓库没有可展示的 README 介绍内容。",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                else -> {
                    val intro = uiState.intro ?: return@ScalingLazyColumn
                    items(intro.paragraphs.size) { i ->
                        Text(
                            intro.paragraphs[i],
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    items(intro.images.size) { i ->
                        AsyncImage(
                            model = intro.images[i],
                            contentDescription = "项目配图 ${i + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            if (canAddToTestHub) {
                item {
                    GrayButton(
                        onClick = {
                            if (!addingToTestHub) {
                                addingToTestHub = true
                                scope.launch {
                                    val r = com.tengwear.tengstore.ui.util.TestHubAdder.add(fullName)
                                    addingToTestHub = false
                                    Toast.makeText(
                                        context,
                                        when (r) {
                                            is com.tengwear.tengstore.ui.util.TestHubAdder.Result.Success -> "已加入测试应用"
                                            com.tengwear.tengstore.ui.util.TestHubAdder.Result.Already -> "该项目已在测试应用中"
                                            com.tengwear.tengstore.ui.util.TestHubAdder.Result.NoAccess -> "当前令牌无权限"
                                            com.tengwear.tengstore.ui.util.TestHubAdder.Result.NoToken -> "请先在设置里登录 GitHub 令牌"
                                            is com.tengwear.tengstore.ui.util.TestHubAdder.Result.Error ->
                                                "添加失败：${r.message}"
                                        },
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        enabled = !addingToTestHub,
                        label = { Text(if (addingToTestHub) "正在添加…" else "添加到测试应用", maxLines = 1) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    )
                }
            }

            item {
                GrayButton(
                    onClick = {
                        com.tengwear.tengstore.ui.util.ShareHelper.share(
                            context,
                            com.tengwear.tengstore.ui.util.ShareHelper.buildShareText(fullName),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("分享该应用", maxLines = 1) },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                )
            }
            item {
                GrayButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("返回", maxLines = 1) },
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                )
            }
        }
    }
}
