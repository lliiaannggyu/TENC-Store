package com.tengwear.tengstore.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
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
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT

/**
 * 主菜单：顶部为公告轮播；列表顺序按使用频率——
 * 搜索 → 测试项目 → 收藏 → 历史记录 → 信息 → 我的下载 → 应用上传 → 服务端推送 → 设置。
 */
@Composable
fun HomeScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val pushRepo by settings.pushRepo.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("TENC Store") } }

            // 顶部公告轮播（索引公告仓库的图片 + 标题，点击看全文）
            item {
                AnnouncementBanner(
                    onOpen = { index -> navController.navigate("announcement/$index") },
                )
            }

            item {
                Chip(
                    onClick = { navController.navigate("search") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索应用", maxLines = 1) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("testhub") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("测试项目", maxLines = 1) },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("favorites") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("收藏", maxLines = 1) },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("history") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("历史记录", maxLines = 1) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("inbox") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("信息", maxLines = 1) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("downloads") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("我的下载", maxLines = 1) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { navController.navigate("upload") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("应用上传", maxLines = 1) },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                )
            }
            if (settings.parseRepo(pushRepo) != null) {
                item {
                    Chip(
                        onClick = { navController.navigate("releases/${pushRepo.trim().replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("服务端推送", maxLines = 1) },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    )
                }
            }
            item {
                Chip(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                )
            }
        }
    }
}
