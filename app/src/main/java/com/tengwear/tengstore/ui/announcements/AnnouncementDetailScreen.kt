package com.tengwear.tengstore.ui.announcements

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import com.tengwear.tengstore.ui.util.RotaryScroll
import coil.compose.AsyncImage
import com.tengwear.tengstore.data.AppContainer

/**
 * 公告详情页：轮播图点进来，展示公告大图与完整正文。
 */
@Composable
fun AnnouncementDetailScreen(navController: NavHostController, index: Int) {
    val viewModel: AnnouncementsViewModel = viewModel()
    val items by AnnouncementsStore.items.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val announcement = items?.getOrNull(index)

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (announcement == null) {
                item { ListHeader { Text("公告") } }
                item { Text("公告不存在或已更新，请返回刷新。", modifier = Modifier.padding(8.dp)) }
            } else {
                item { ListHeader { Text(announcement.title, maxLines = 3) } }

                announcement.imageUrl?.let { url ->
                    item {
                        AsyncImage(
                            model = url,
                            contentDescription = announcement.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .height(110.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    }
                }

                item {
                    Text(
                        announcement.body.ifBlank { "（公告无正文）" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}
