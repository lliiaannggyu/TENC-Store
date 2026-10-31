package com.example.weargithubstore.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.items
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.ui.util.LIST_CHIP_HEIGHT

/** 收藏夹：收藏的仓库列表，点击进详情（可下载 / 取消收藏）。 */
@Composable
fun FavoritesScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val favorites by settings.favorites.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("收藏") } }

            if (favorites.isEmpty()) {
                item {
                    Text(
                        "收藏夹为空。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                items(favorites.sorted().size) { i ->
                    val fullName = favorites.sorted()[i]
                    Chip(
                        onClick = { navController.navigate("detail/${fullName.replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(fullName.substringAfter("/"), maxLines = 1) },
                        secondaryLabel = { Text(fullName, maxLines = 1) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        colors = ChipDefaults.primaryChipColors(),
                    )
                }
            }
        }
    }
}

/** 浏览历史：最近查看过的仓库（最新在前，最多 20 条），可清空。 */
@Composable
fun HistoryScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val history by settings.history.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("历史记录") } }

            if (history.isEmpty()) {
                item {
                    Text(
                        "暂无浏览记录。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                items(history.size) { i ->
                    val fullName = history[i]
                    Chip(
                        onClick = { navController.navigate("detail/${fullName.replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(fullName.substringAfter("/"), maxLines = 1) },
                        secondaryLabel = { Text(fullName, maxLines = 1) },
                        icon = { Icon(Icons.Default.Android, contentDescription = null) },
                    )
                }
                item {
                    Chip(
                        onClick = { settings.clearHistory() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("清空历史", maxLines = 1) },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        colors = ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
    }
}
