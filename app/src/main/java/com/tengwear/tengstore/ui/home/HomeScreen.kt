package com.tengwear.tengstore.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.testhub.ProjectChip
import com.tengwear.tengstore.ui.testhub.ProjectDetailScreen
import com.tengwear.tengstore.ui.testhub.TestHubViewModel
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star

/**
 * 首页：三页左右滑动切换。
 * 页1「测试」：顶部公告轮播（4:3）+ 测试应用列表裸露展示；
 * 页2「搜索」：GitHub 仓库 / 用户搜索、随机抽取含 APK 项目、关键词设置；
 * 页3「更多」：收藏、历史记录、信息、我的下载、应用上传、服务端推送、设置。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val pagerState = rememberPagerState { 3 }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            when (page) {
                0 -> TestPage(navController)
                1 -> SearchPage(navController)
                else -> MorePage(navController)
            }
        }

        // 页面指示器
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val active = i == pagerState.currentPage
                Box(
                    modifier = Modifier.padding(horizontal = 4.dp).size(7.dp),
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

/** 页1：公告轮播（4:3）+ 测试应用列表裸露。 */
@Composable
private fun TestPage(navController: NavHostController) {
    val viewModel: TestHubViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<com.tengwear.tengstore.ui.testhub.TestProject?>(null) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.load() }

    selected?.let { sel ->
        uiState.projects.firstOrNull { it.fullName == sel.fullName }?.let { selected = it }
    }

    val detail = selected
    if (detail != null) {
        ProjectDetailScreen(
            project = detail,
            onBack = { selected = null },
            onDownload = {
                navController.navigate("releases/${detail.fullName.replace("/", "~~")}")
            },
            onFeedback = {
                val dev = detail.devEmail?.takeIf { it.isNotBlank() } ?: "none"
                navController.navigate(
                    "feedback/${detail.fullName.replace("/", "~~")}/${java.net.URLEncoder.encode(dev, "UTF-8")}",
                )
            },
        )
        return
    }

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // 顶部公告轮播（4:3）
            item {
                AnnouncementBanner(
                    onOpen = { index -> navController.navigate("announcement/$index") },
                )
            }

            item { ListHeader { Text("测试项目") } }
            if (uiState.loading && uiState.projects.isEmpty()) {
                item { Text("读取索引中…", modifier = Modifier.padding(8.dp)) }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            items(uiState.projects.size) { i ->
                val project = uiState.projects[i]
                ProjectChip(project = project) { selected = project }
            }
            if (uiState.projects.isNotEmpty()) {
                item {
                    Button(
                        onClick = { navController.navigate("testhub") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("查看全部", maxLines = 1) },
                        icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                    )
                }
            }
        }
    }
}

/** 页2：GitHub 搜索（仓库 / 用户 / 随机抽取 / 关键词设置）。 */
@Composable
private fun SearchPage(navController: NavHostController) {
    val drawViewModel: RandomDrawViewModel = viewModel()
    val drawState by drawViewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    val userInputLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.tengwear.tengstore.ui.util.parseTextInputResult(result.data)?.let { query ->
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            navController.navigate("user_search/$encoded")
        }
    }

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("GitHub 搜索") } }

            item {
                Button(
                    onClick = { navController.navigate("search") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索仓库", maxLines = 1) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = {
                        com.tengwear.tengstore.ui.util.launchTextInputSafely(userInputLauncher, "搜索 GitHub 用户")
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索用户", maxLines = 1) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = {
                        drawViewModel.draw { fullName ->
                            navController.navigate("detail/${fullName.replace("/", "~~")}")
                        }
                    },
                    enabled = !drawState.busy,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = {
                        Text(
                            if (drawState.busy) "抽取中…（含 APK 项目）" else "随机抽一个应用",
                            maxLines = 1,
                        )
                    },
                    icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                )
            }
            drawState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            item {
                Button(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("关键词设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
    }
}

/** 页3：其他功能入口。 */
@Composable
private fun MorePage(navController: NavHostController) {
    val settings = AppContainer.settings
    val pushRepo by settings.pushRepo.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("更多功能") } }

            item {
                Button(
                    onClick = { navController.navigate("favorites") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("收藏", maxLines = 1) },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate("history") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("历史记录", maxLines = 1) },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate("inbox") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("信息", maxLines = 1) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate("downloads") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("我的下载", maxLines = 1) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { navController.navigate("upload") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("应用上传", maxLines = 1) },
                    icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                )
            }
            if (settings.parseRepo(pushRepo) != null) {
                item {
                    Button(
                        onClick = { navController.navigate("releases/${pushRepo.trim().replace("/", "~~")}") },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("服务端推送", maxLines = 1) },
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    )
                }
            }
            item {
                Button(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                )
            }
        }
    }
}
