package com.tengwear.tengstore.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.tengwear.tengstore.ui.announcements.Announcement
import com.tengwear.tengstore.ui.announcements.AnnouncementsStore

/**
 * 主菜单顶部公告轮播：
 * 每页 = 公告仓库的 banner 图（无图则标题卡片）+ 底部「公告」标签与标题，
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
            AnnouncementCard(
                announcement = list[page],
                onClick = { onOpen(page) },
                modifier = Modifier.fillMaxWidth(0.96f).padding(horizontal = 5.dp),
            )
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

        // 圆点指示器
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(list.size) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (active) 7.dp else 5.dp, height = if (active) 7.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color.Gray.copy(alpha = 0.5f)),
                )
            }
        }
    }
}

/** 单页公告卡片：banner 图或标题卡片，底部叠加「公告」标签与标题。 */
@Composable
private fun AnnouncementCard(
    announcement: Announcement,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF202124))
            .clickable(onClick = onClick),
    ) {
        if (announcement.imageUrl != null) {
            AsyncImage(
                model = announcement.imageUrl,
                contentDescription = announcement.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(104.dp),
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
        // 底部叠加：公告标签 + 标题
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "公告",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3D82F0))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                announcement.title,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
