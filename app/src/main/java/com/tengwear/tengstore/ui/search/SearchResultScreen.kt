package com.tengwear.tengstore.ui.search

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.domain.model.Repo
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/**
 * 搜索结果页（独立页面）：
 * - 进入后自动按关键词发起搜索；
 * - 搜索中展示整页加载动画；
 * - 结果逐条列出，点按进入该仓库的 Release 列表。
 */
@Composable
fun SearchResultScreen(navController: NavHostController, query: String) {
    val viewModel: SearchViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(query) { viewModel.search(query) }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        if (uiState.loading) {
            // 独立加载页：整页旋转进度 + 提示文字
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScalingLazyColumn {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    item {
                        Text(
                            "正在搜索「$query」…",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            return@ScreenScaffold
        }

        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("搜索结果") } }

            uiState.error?.let { err ->
                item { Text("出错：$err", modifier = Modifier.padding(8.dp)) }
            }

            if (uiState.error == null && uiState.repos.isEmpty()) {
                item {
                    Text(
                        "没有找到与「$query」相关的仓库。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (uiState.repos.isNotEmpty()) {
                if (uiState.totalBeforeFilter > uiState.repos.size) {
                    item {
                        Text(
                            "已按关键词过滤：${uiState.repos.size}/${uiState.totalBeforeFilter}",
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(uiState.repos.size) { i ->
                    RepoChip(repo = uiState.repos[i]) {
                        // 进项目详情页：README 介绍 + 图片 + 收藏 / 历史记录
                        navController.navigate("detail/${uiState.repos[i].fullName.replace("/", "~~")}")
                    }
                }
            }

            item {
                GrayButton(
                    onClick = { viewModel.search(query) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("重新搜索", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun RepoChip(repo: Repo, onClick: () -> Unit) {
    GrayButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
        label = { Text(repo.name, maxLines = 1) },
        secondaryLabel = {
            Text(
                buildString {
                    append(repo.fullName)
                    if (repo.stars > 0) append("  ★ ${repo.stars}")
                },
                maxLines = 1,
            )
        },
        icon = { com.tengwear.tengstore.ui.util.RepoIcon(repo.fullName, Modifier.size(22.dp)) },
    )
}
