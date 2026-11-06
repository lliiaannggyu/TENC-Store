package com.tengwear.tengstore.ui.announcements

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.edgeSpacers

/** 公告页：展示公告索引仓库 announcements.md 的内容，点按查看公告正文。 */
@Composable
fun AnnouncementsScreen(navController: NavHostController) {
    val viewModel: AnnouncementsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.load() }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("公告") } }

            if (uiState.loading) {
                item { Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("加载中…") }) }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            if (!uiState.loading && uiState.error == null && uiState.items.isEmpty()) {
                item { Text("暂无公告。", modifier = Modifier.padding(8.dp)) }
            }

            items(uiState.items.size) { i ->
                val announcement = uiState.items[i]
                Button(
                    onClick = { navController.navigate("announcement/$i") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text(announcement.title, maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            announcement.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "",
                            maxLines = 1,
                        )
                    },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                    colors = ButtonDefaults.buttonColors(),
                )
            }

            item {
                Button(
                    onClick = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("刷新公告", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }
}
