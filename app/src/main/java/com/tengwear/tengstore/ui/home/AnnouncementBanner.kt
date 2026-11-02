package com.tengwear.tengstore.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.tengwear.tengstore.ui.announcements.Announcement
import com.tengwear.tengstore.ui.announcements.AnnouncementsStore

/**
 * 主菜单顶部公告轮播：
 * 每页 = 公告仓库的 banner 图（无图则标题卡片），
 * 底部圆点指示器；点按任意一页进入公告详情页。
 * 无操作时每 4 秒自动轮播到下一页。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementBanner(
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by AnnouncementsStore.items.collectAsState()

    LaunchedEffect(Unit) { AnnouncementsStore.load() }

    val list = items ?: return
    if (list.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        val pagerState = rememberPagerState { list.size }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnnouncementCard(
                    announcement = list[page],
                    onClick = { onOpen(page) },
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
        }

        // 自动轮播：每 4 秒翻到下一页（用户正在滑动时暂停）
        LaunchedEffect(pagerState, list.size) {
            if (list.size > 1) {
                while (true) {
                    kotlinx.coroutines.delay(4000)
                    if (!pagerState.isScrollInProgress) {
                        pagerState.animateScrollToPage((pagerState.currentPage + 1) % list.size)
                    }
                }
            }
        }

        // 圆点指示器（统一外框尺寸，保证左右对称、高亮居中）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(list.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.Gray.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

/** 单页公告卡片：banner 图或标题卡片（无图时居中显示标题）。 */
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF202124))
            .clickable(onClick = onClick),
    ) {
        if (announcement.imageUrl != null) {
            AsyncImage(
                model = announcement.imageUrl,
                contentDescription = announcement.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                announcement.title,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
