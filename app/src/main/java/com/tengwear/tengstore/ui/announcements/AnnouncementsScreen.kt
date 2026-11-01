package com.tengwear.tengstore.ui.announcements

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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT

/** 公告页：展示公告索引仓库 announcements.md 的内容，点按查看公告正文。 */
@Composable
fun AnnouncementsScreen(navController: NavHostController) {
    val viewModel: AnnouncementsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("公告") } }

            if (uiState.loading) {
                item { Chip(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("加载中…") }) }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            if (!uiState.loading && uiState.error == null && uiState.items.isEmpty()) {
                item { Text("暂无公告。", modifier = Modifier.padding(8.dp)) }
            }

            items(uiState.items.size) { i ->
                val announcement = uiState.items[i]
                Chip(
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
                    colors = ChipDefaults.primaryChipColors(),
                )
            }

            item {
                Chip(
                    onClick = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("刷新公告", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }
}
