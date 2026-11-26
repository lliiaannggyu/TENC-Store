package com.tengwear.tengstore.ui.testhub

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.tengwear.tengstore.ui.util.pullToRefresh
import com.tengwear.tengstore.ui.util.rememberPullToRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.NewTagBadge
import com.tengwear.tengstore.ui.util.RepoIcon
import com.tengwear.tengstore.ui.util.formatSize
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/**
 * 测试项目页（单开页面）：展示索引仓库 repos.txt 中的所有待测试项目。
 * 点击项目 → 项目详情（包名、体积、开发者等），
 * 详情内提供「下载」与「反馈」（建议 / Bug）两个按钮。
 */
@Composable
fun TestHubScreen(navController: NavHostController) {
    val viewModel: TestHubViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val pullState = rememberPullToRefreshState()

    // 进应用只索引一次：缓存命中时 load() 直接复用缓存，不再重复索引
    LaunchedEffect(Unit) { viewModel.load() }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = null,
            modifier = Modifier.fillMaxSize().pullToRefresh(
                state = pullState,
                listState = listState,
                onRefresh = { viewModel.load(force = true) },
            ),
        ) {
        edgeSpacers()
            if (pullState.pullProgress.floatValue > 0f) {
                item {
                    Text(
                        if (pullState.pullProgress.floatValue >= 1f) "松开刷新索引…" else "下拉刷新索引…",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            item { ListHeader { Text("测试项目") } }

            item {
                Text(
                    "索引：${uiState.hubRepo.ifBlank { "未配置" }}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (uiState.loading) {
                item {
                    GrayButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("读取索引中…") })
                }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }

            items(uiState.projects.size) { i ->
                val project = uiState.projects[i]
                ProjectChip(project = project) {
                    navController.navigate("project_detail/${project.fullName.replace("/", "~~")}")
                }
            }

            if (uiState.projects.isNotEmpty()) {
                item {
                    GrayButton(
                        onClick = { viewModel.load(force = true) },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("刷新", maxLines = 1) },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/**
 * 项目详情独立路由页（WearApp 中注册为 project_detail/{fullName}）。
 *
 * 关键修复：详情现在是导航栈上的独立页面，右滑（SwipeToDismissBox）与返回键
 * 由 SwipeDismissableNavHost 处理，会 popBackStack 回到列表页。此前详情与列表
 * 共用同一个 composable、仅用 selected 状态切换，手势作用于整个 home/testhub，
 * 所以"右滑退回列表"无效——这正是用户反馈的问题。
 */
@Composable
fun ProjectDetailRoute(navController: NavHostController, fullName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by TestHubStore.uiState.collectAsState()

    LaunchedEffect(Unit) {
        // 缓存命中直接返回；进程重建等缓存为空时重新索引一次
        TestHubStore.load()
    }

    val project = uiState.projects.firstOrNull { it.fullName == fullName }
    LaunchedEffect(project?.latest?.tagName) {
        project?.latest?.let { rel -> TestHubStore.markSeen(fullName, rel.tagName) }
    }

    if (project != null) {
        ProjectDetailScreen(
            project = project,
            onBack = { navController.popBackStack() },
            onDownload = {
                navController.navigate("releases/${fullName.replace("/", "~~")}")
            },
            onFeedback = {
                val dev = project.devEmail?.takeIf { it.isNotBlank() } ?: "none"
                navController.navigate(
                    "feedback/${fullName.replace("/", "~~")}/${java.net.URLEncoder.encode(dev, "UTF-8")}",
                )
            },
            onShare = {
                com.tengwear.tengstore.ui.util.ShareHelper.share(
                    context,
                    com.tengwear.tengstore.ui.util.ShareHelper.buildShareText(fullName),
                )
            },
        )
        return
    }

    // 缓存尚未就绪或项目已从索引移除：兜底页，同样可右滑/返回回到列表
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    ScreenScaffold(scrollState = listState, timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
            edgeSpacers()
            item { ListHeader { Text("项目详情") } }
            item {
                Text(
                    if (uiState.loading) "项目信息加载中…" else "未找到该项目（可能已从索引移除）",
                    modifier = Modifier.padding(8.dp),
                )
            }
            item {
                GrayButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("返回列表", maxLines = 1) },
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                )
            }
        }
    }
}

/** 项目详情：包名（APK 包）、体积、开发者等信息 + 下载 / 反馈按钮。 */
@Composable
internal fun ProjectDetailScreen(
    project: TestProject,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onFeedback: () -> Unit,
    onShare: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val detailContext = androidx.compose.ui.platform.LocalContext.current
    val release = project.latest
    val apks = release?.apkAssets ?: emptyList()

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text(project.title) } }
            item { com.tengwear.tengstore.ui.util.RepoNameTranslation(project.fullName) }

            item {
                Text(
                    buildString {
                        appendLine("项目：${project.fullName}")
                        project.displayName?.let { appendLine("名称：$it") }
                        if (project.loading) {
                            appendLine("状态：加载中…")
                        } else if (release == null) {
                            appendLine("状态：${project.error ?: "暂无可用版本"}")
                        } else {
                            appendLine("最新版本：${release.tagName}")
                            if (apks.isNotEmpty()) {
                                appendLine("包名：${apks.first().name}")
                                appendLine("体积：${formatSize(apks.sumOf { it.size })}")
                            }
                            appendLine("开发者：${project.devEmail ?: "未提供"}")
                            appendLine("发布时间：${release.publishedAt ?: "未知"}")
                        }
                    },
                    modifier = Modifier.padding(8.dp),
                )
            }

            item {
                GrayButton(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = apks.isNotEmpty(),
                    label = { Text("下载", maxLines = 1) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                )
            }
            if (release != null && release!!.assets.isNotEmpty()) {
                item {
                    GrayButton(
                        onClick = {
                            val n = com.tengwear.tengstore.data.download.DownloadQueue
                                .enqueue(project.fullName, release.tagName, release.assets)
                            android.widget.Toast.makeText(
                                detailContext,
                                "已加入 $n 个文件到下载队列",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = {
                            Text("下载全部文件（${release.assets.size}）", maxLines = 1)
                        },
                        icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    )
                }
            }
            item {
                GrayButton(
                    onClick = onFeedback,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("反馈", maxLines = 1) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
                )
            }
            item {
                GrayButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("分享该应用", maxLines = 1) },
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                )
            }

            item { ListHeader { Text("项目介绍") } }
            when {
                project.introLoading -> {
                    item {
                        Text("介绍加载中…", modifier = Modifier.padding(8.dp))
                    }
                }
                project.intro == null ||
                    (project.intro.paragraphs.isEmpty() && project.intro.images.isEmpty()) -> {
                    item {
                        Text("该仓库没有可展示的 README 介绍内容。", modifier = Modifier.padding(8.dp))
                    }
                }
                else -> {
                    val intro = project.intro
                    items(intro.paragraphs.size) { i ->
                        Text(
                            intro.paragraphs[i],
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    items(intro.images.size) { i ->
                        AsyncImage(
                            model = intro.images[i],
                            contentDescription = "项目配图 ${i + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            item {
                GrayButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("返回列表", maxLines = 1) },
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                )
            }

            if (project.devEmail == null) {
                item {
                    Text(
                        "该项目未附带开发者邮箱，反馈不可用；开发者可在索引仓库 repos.txt 中为项目附加加密邮箱。",
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProjectChip(project: TestProject, onClick: () -> Unit) {
    val name = project.title
    when {
        project.loading -> {
            GrayButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = { Text(name, maxLines = 1) },
                secondaryLabel = { Text("加载中…", maxLines = 1) },
                icon = { RepoIcon(project.fullName, Modifier.size(22.dp)) },
            )
        }
        project.error != null -> {
            GrayButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = { Text(name, maxLines = 1) },
                secondaryLabel = { Text(project.error ?: "", maxLines = 1) },
                icon = { RepoIcon(project.fullName, Modifier.size(22.dp)) },
            )
        }
        else -> {
            val release = project.latest
            val apks = release?.apkAssets ?: emptyList()
            GrayButton(
                onClick = onClick,
                enabled = release != null,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, maxLines = 1)
                        if (project.hasUpdate) {
                            NewTagBadge(modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                },
                secondaryLabel = {
                    Text(
                        buildString {
                            append(release?.tagName ?: "")
                            if (apks.isNotEmpty()) {
                                append(" · ${formatSize(apks.sumOf { it.size })}")
                            }
                        },
                        maxLines = 1,
                    )
                },
                icon = { RepoIcon(project.fullName, Modifier.size(22.dp)) },
            )
        }
    }
}
