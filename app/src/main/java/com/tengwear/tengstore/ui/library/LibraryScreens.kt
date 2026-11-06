package com.tengwear.tengstore.ui.library

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.edgeSpacers

/** 收藏夹：收藏的仓库列表，点击进详情（可下载 / 取消收藏）。 */
@Composable
fun FavoritesScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val favorites by settings.favorites.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
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
                    Button(
                        onClick = { navController.navigate("detail/${fullName.replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(fullName.substringAfter("/"), maxLines = 1) },
                        secondaryLabel = { Text(fullName, maxLines = 1) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        colors = ButtonDefaults.buttonColors(),
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
    RotaryScroll(listState)

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
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
                    Button(
                        onClick = { navController.navigate("detail/${fullName.replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text(fullName.substringAfter("/"), maxLines = 1) },
                        secondaryLabel = { Text(fullName, maxLines = 1) },
                        icon = { Icon(Icons.Default.Android, contentDescription = null) },
                    )
                }
                item {
                    Button(
                        onClick = { settings.clearHistory() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("清空历史", maxLines = 1) },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
            }
        }
    }
}
