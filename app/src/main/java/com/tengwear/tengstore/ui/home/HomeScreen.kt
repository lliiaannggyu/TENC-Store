package com.tengwear.tengstore.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.testhub.ProjectDetailScreen
import com.tengwear.tengstore.ui.testhub.TestProject
import com.tengwear.tengstore.ui.testhub.TestHubViewModel
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.StackedCapsule
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
 * 首页：三页左右滑动切换，堆叠列表风格（AppOpsPro 种子配色 + 条形胶囊卡）。
 * 页1「测试」：顶部公告轮播（4:3）+ 测试应用列表裸露展示；
 * 页2「搜索」：GitHub 仓库 / 用户搜索、随机抽取含 APK 项目、关键词设置；
 * 页3「更多」：收藏、历史记录、信息、我的下载、应用上传、服务端推送、设置。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val pagerState = rememberPagerState { 3 }

    Box(modifier = Modifier.fillMaxSize()) {
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
        // 全局时间（各页共用）
        TimeText(modifier = Modifier.align(Alignment.TopCenter))
    }
}

/** 堆叠列表的标准页头：随堆叠变换收窄、变暗。 */
@Composable
private fun TransformingLazyColumnItemScope.StackedHeader(title: String, spec: TransformationSpec) {
    ListHeader(
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, spec),
        transformation = SurfaceTransformation(spec),
    ) {
        Text(title)
    }
}

/** 页1：公告轮播（4:3）+ 测试应用列表裸露（堆叠胶囊）。 */
@Composable
private fun TestPage(navController: NavHostController) {
    val viewModel: TestHubViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<TestProject?>(null) }
    val listState = rememberTransformingLazyColumnState()
    RotaryScroll(listState)
    val spec = rememberTransformationSpec()

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

    TransformingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // 顶部公告轮播（4:3，不参与堆叠变换）
        item {
            AnnouncementBanner(
                onOpen = { index -> navController.navigate("announcement/$index") },
            )
        }

        item { StackedHeader("测试项目", spec) }
        if (uiState.loading && uiState.projects.isEmpty()) {
            item { Text("读取索引中…", modifier = Modifier.padding(8.dp)) }
        }
        uiState.error?.let { err ->
            item { Text(err, modifier = Modifier.padding(8.dp)) }
        }
        items(uiState.projects.size) { i ->
            val project = uiState.projects[i]
            StackedCapsule(
                onClick = { selected = project },
                leading = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = {
                    Text(
                        project.fullName.substringAfter("/"),
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
                secondaryLabel = {
                    if (project.loading) {
                        Text("加载中…", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                    } else {
                        project.latest?.tagName?.let {
                            Text(it, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                },
            )
        }
        if (uiState.projects.isNotEmpty()) {
            item {
                StackedCapsule(
                    onClick = { navController.navigate("testhub") },
                    leading = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("查看全部", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
                )
            }
        }
    }
}

/** 页2：GitHub 搜索（仓库 / 用户 / 随机抽取 / 关键词设置）。 */
@Composable
private fun SearchPage(navController: NavHostController) {
    val drawViewModel: RandomDrawViewModel = viewModel()
    val drawState by drawViewModel.state.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    RotaryScroll(listState)
    val spec = rememberTransformationSpec()

    val userInputLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.tengwear.tengstore.ui.util.parseTextInputResult(result.data)?.let { query ->
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            navController.navigate("user_search/$encoded")
        }
    }

    TransformingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { StackedHeader("GitHub 搜索", spec) }

        item {
            StackedCapsule(
                onClick = { navController.navigate("search") },
                leading = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("搜索仓库", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = {
                    com.tengwear.tengstore.ui.util.launchTextInputSafely(userInputLauncher, "搜索 GitHub 用户")
                },
                leading = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("搜索用户", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = {
                    drawViewModel.draw { fullName ->
                        navController.navigate("detail/${fullName.replace("/", "~~")}")
                    }
                },
                leading = { Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = {
                    Text(
                        if (drawState.busy) "抽取中…" else "随机抽一个应用",
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
                secondaryLabel = {
                    Text(
                        if (drawState.busy) "正在找含 APK 的项目" else "只抽带 APK 的项目，不重复",
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                },
            )
        }
        drawState.error?.let { err ->
            item { Text(err, modifier = Modifier.padding(8.dp)) }
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("settings") },
                leading = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("关键词设置", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
    }
}

/** 页3：其他功能入口。 */
@Composable
private fun MorePage(navController: NavHostController) {
    val settings = AppContainer.settings
    val pushRepo by settings.pushRepo.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    RotaryScroll(listState)
    val spec = rememberTransformationSpec()

    TransformingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { StackedHeader("更多功能", spec) }

        item {
            StackedCapsule(
                onClick = { navController.navigate("favorites") },
                leading = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("收藏", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("history") },
                leading = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("历史记录", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("inbox") },
                leading = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("信息", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("downloads") },
                leading = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("我的下载", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("upload") },
                leading = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("应用上传", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
        if (settings.parseRepo(pushRepo) != null) {
            item {
                StackedCapsule(
                    onClick = { navController.navigate("releases/${pushRepo.trim().replace("/", "~~")}") },
                    leading = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("服务端推送", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
                )
            }
        }
        item {
            StackedCapsule(
                onClick = { navController.navigate("settings") },
                leading = { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text("设置", maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp)) },
            )
        }
    }
}
