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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.MailClient
import com.tengwear.tengstore.ui.inbox.InboxItem
import com.tengwear.tengstore.ui.inbox.InboxViewModel
import com.tengwear.tengstore.ui.testhub.ProjectDetailScreen
import com.tengwear.tengstore.ui.testhub.TestProject
import com.tengwear.tengstore.ui.testhub.TestHubViewModel
import com.tengwear.tengstore.ui.util.GrayButton
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.NewTagBadge
import com.tengwear.tengstore.ui.util.RepoIcon
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import com.tengwear.tengstore.ui.util.edgeSpacers

/**
 * 首页：三页左右滑动切换。
 * 页1「测试」：顶部公告轮播（4:3）+ 测试应用列表裸露展示；
 * 页2「搜索」：GitHub 仓库 / 用户搜索、随机抽取含 APK 项目、关键词设置；
 * 页3「更多」：收藏、收藏用户、历史记录、信息、我的下载、应用上传、服务端推送、设置。
 * 所有列表按钮统一灰色（GrayButton）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val pagerState = rememberPagerState { 3 }

    // 开屏检查：新消息提示 + 投稿审核结果全屏提醒
    var msgHintCount by remember { mutableStateOf(0) }
    var reviewItem by remember { mutableStateOf<InboxItem?>(null) }
    var fetchedMails by remember { mutableStateOf<List<MailClient.InboxMail>?>(null) }

    LaunchedEffect(Unit) {
        val settings = AppContainer.settings
        val email = settings.developerEmail.value
        val code = settings.smtpAuthCode.value
        if (email.isBlank() || code.isBlank()) return@LaunchedEffect
        val (mails, _) = MailClient.fetchVerifiedInbox(
            email = email,
            authCode = code,
            subjectKeyword = InboxViewModel.LOG_SUBJECT,
            sinceHours = 72,
            windowMinutes = 7 * 24 * 60,
        )
        fetchedMails = mails
        if (mails.isEmpty()) return@LaunchedEffect
        val newReview = mails
            .filter { it.subject.contains("投稿结果") && it.receivedAt > settings.lastReviewSeenAt }
            .maxByOrNull { it.receivedAt }
        if (newReview != null) {
            reviewItem = InboxViewModel.parseFields(newReview)
        } else {
            msgHintCount = mails.count {
                !it.subject.contains("投稿结果") && it.receivedAt > settings.lastInboxSeenAt
            }
        }
    }

    fun dismissReview() {
        val settings = AppContainer.settings
        fetchedMails
            ?.filter { it.subject.contains("投稿结果") }
            ?.maxOfOrNull { it.receivedAt }
            ?.let { settings.lastReviewSeenAt = it }
        reviewItem = null
        // 审核提醒关闭后，再提示普通新信息
        msgHintCount = fetchedMails?.count {
            !it.subject.contains("投稿结果") && it.receivedAt > settings.lastInboxSeenAt
        } ?: 0
    }

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
        }
        // 全局时间（各页共用）
        TimeText(modifier = Modifier.align(Alignment.TopCenter))

        // 投稿审核结果：全屏提醒
        reviewItem?.let { item ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF15161A)),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "🔔 审核结果",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        item.link ?: item.project ?: "未知仓库",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        "结果：${item.reviewResult ?: "打回"}",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "理由：${item.reviewReason?.ifBlank { "无" } ?: "无"}",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    GrayButton(
                        onClick = { dismissReview() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(LIST_CHIP_HEIGHT),
                        label = { Text("知道了", maxLines = 1) },
                    )
                }
            }
        }
    }

    // 新消息提示（审核提醒优先；全屏提醒关闭后才弹）
    msgHintCount.takeIf { it > 0 && reviewItem == null }?.let { count ->
        androidx.wear.compose.material3.AlertDialog(
            visible = true,
            onDismissRequest = { msgHintCount = 0 },
            confirmButton = {
                GrayButton(
                    onClick = { msgHintCount = 0 },
                    label = { Text("知道了") },
                )
            },
            title = { Text("新消息", maxLines = 1) },
            text = { Text("有 $count 条新信息，请到「更多 → 信息」查看") },
        )
    }
}

/** 页1：公告轮播（4:3）+ 下载状态条 + 测试应用列表裸露。 */
@Composable
private fun TestPage(navController: NavHostController) {
    val viewModel: TestHubViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<TestProject?>(null) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    // 全局下载状态：正在下载显示进度条，刚结束显示提示条（8 秒后自动消失）
    val downloadActive by com.tengwear.tengstore.data.download.DownloadTracker
        .active.collectAsState()
    val downloadFinished by com.tengwear.tengstore.data.download.DownloadTracker
        .finished.collectAsState()

    LaunchedEffect(downloadFinished) {
        if (downloadFinished != null) {
            kotlinx.coroutines.delay(8000)
            com.tengwear.tengstore.data.download.DownloadTracker.consumeFinished()
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    // 打开详情即视为看过当前版本，「有更新」表情随之消失
    LaunchedEffect(selected?.fullName) {
        selected?.let { sel ->
            sel.latest?.let { rel -> viewModel.markSeen(sel.fullName, rel.tagName) }
        }
    }

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

    ScalingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        edgeSpacers()
        // 顶部公告轮播（4:3）
        item {
            AnnouncementBanner(
                onOpen = { index -> navController.navigate("announcement/$index") },
            )
        }

        // 下载状态条：正在下载（进度）/ 下载结束（提示）
        if (downloadActive != null || downloadFinished != null) {
            item {
                com.tengwear.tengstore.ui.util.DownloadStatusBar(
                    active = downloadActive,
                    finished = downloadFinished,
                    onFinishedDismiss = {
                        com.tengwear.tengstore.data.download.DownloadTracker.consumeFinished()
                    },
                )
            }
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
            val name = project.fullName.substringAfter("/")
            GrayButton(
                onClick = { selected = project },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { RepoIcon(project.fullName, Modifier.size(22.dp)) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, maxLines = 1)
                        if (project.hasUpdate) {
                            NewTagBadge(modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                },
                secondaryLabel = {
                    if (project.loading) {
                        Text("加载中…", maxLines = 1)
                    } else {
                        project.latest?.tagName?.let { Text(it, maxLines = 1) }
                    }
                },
            )
        }
        if (uiState.projects.isNotEmpty()) {
            item {
                GrayButton(
                    onClick = { navController.navigate("testhub") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("查看全部", maxLines = 1) },
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

    ScalingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        edgeSpacers()
        item { ListHeader { Text("GitHub 搜索") } }

        item {
            GrayButton(
                onClick = { navController.navigate("search") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("搜索仓库", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = {
                    com.tengwear.tengstore.ui.util.launchTextInputSafely(userInputLauncher, "搜索 GitHub 用户")
                },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                label = { Text("搜索用户", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = {
                    drawViewModel.draw { fullName ->
                        navController.navigate("detail/${fullName.replace("/", "~~")}")
                    }
                },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                label = {
                    Text(
                        if (drawState.busy) "抽取中…" else "随机抽一个应用",
                        maxLines = 1,
                    )
                },
            )
        }
        drawState.error?.let { err ->
            item { Text(err, modifier = Modifier.padding(8.dp)) }
        }
        item {
            GrayButton(
                onClick = { navController.navigate("settings") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("关键词设置", maxLines = 1) },
            )
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

    ScalingLazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        edgeSpacers()
        item { ListHeader { Text("更多功能") } }

        item {
            GrayButton(
                onClick = { navController.navigate("favorites") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                label = { Text("收藏", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("favorite_users") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                label = { Text("收藏用户", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("history") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.History, contentDescription = null) },
                label = { Text("历史记录", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("inbox") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Email, contentDescription = null) },
                label = { Text("信息", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("downloads") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                label = { Text("我的下载", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("upload") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                label = { Text("应用上传", maxLines = 1) },
            )
        }
        item {
            GrayButton(
                onClick = { navController.navigate("submit") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Send, contentDescription = null) },
                label = { Text("应用投稿", maxLines = 1) },
            )
        }
        if (settings.parseRepo(pushRepo) != null) {
            item {
                GrayButton(
                    onClick = { navController.navigate("releases/${pushRepo.trim().replace("/", "~~")}") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("服务端推送", maxLines = 1) },
                )
            }
        }
        item {
            GrayButton(
                onClick = { navController.navigate("settings") },
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("设置", maxLines = 1) },
            )
        }
    }
}
