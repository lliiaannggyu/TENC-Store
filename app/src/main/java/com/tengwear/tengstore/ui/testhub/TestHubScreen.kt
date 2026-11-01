package com.tengwear.tengstore.ui.testhub

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize

/**
 * 测试项目页（单开页面）：展示索引仓库 repos.txt 中的所有待测试项目。
 * 点击项目 → 项目详情（包名、体积、开发者等），
 * 详情内提供「下载」与「反馈」（建议 / Bug）两个按钮。
 */
@Composable
fun TestHubScreen(navController: NavHostController) {
    val viewModel: TestHubViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<TestProject?>(null) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.load() }

    // 详情页跟随列表数据刷新
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
                // devEmail 为空时用 "none" 占位，避免空路由段
                val dev = detail.devEmail?.takeIf { it.isNotBlank() } ?: "none"
                navController.navigate(
                    "feedback/${detail.fullName.replace("/", "~~")}/${java.net.URLEncoder.encode(dev, "UTF-8")}",
                )
            },
        )
        return
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("测试项目") } }

            item {
                Text(
                    "索引：${uiState.hubRepo.ifBlank { "未配置" }}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (uiState.loading) {
                item {
                    Chip(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("读取索引中…") })
                }
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
                    Chip(
                        onClick = { viewModel.load() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        label = { Text("刷新", maxLines = 1) },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** 项目详情：包名（APK 包）、体积、开发者等信息 + 下载 / 反馈按钮。 */
@Composable
private fun ProjectDetailScreen(
    project: TestProject,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onFeedback: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)
    val release = project.latest
    val apks = release?.apkAssets ?: emptyList()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(project.fullName.substringAfter("/")) } }

            item {
                Text(
                    buildString {
                        appendLine("项目：${project.fullName}")
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
                Chip(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = apks.isNotEmpty(),
                    label = { Text("下载", maxLines = 1) },
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }
            item {
                Chip(
                    onClick = onFeedback,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("反馈", maxLines = 1) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null) },
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
                Chip(
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
private fun ProjectChip(project: TestProject, onClick: () -> Unit) {
    when {
        project.loading -> {
            Chip(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = { Text(project.fullName.substringAfter("/"), maxLines = 1) },
                secondaryLabel = { Text("加载中…", maxLines = 1) },
                icon = { Icon(Icons.Default.Android, contentDescription = null) },
            )
        }
        project.error != null -> {
            Chip(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = { Text(project.fullName.substringAfter("/"), maxLines = 1) },
                secondaryLabel = { Text(project.error ?: "", maxLines = 1) },
                icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        else -> {
            val release = project.latest
            val apks = release?.apkAssets ?: emptyList()
            Chip(
                onClick = onClick,
                enabled = release != null,
                modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                label = { Text(project.fullName.substringAfter("/"), maxLines = 1) },
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
                icon = { Icon(Icons.Default.Android, contentDescription = null) },
                colors = ChipDefaults.primaryChipColors(),
            )
        }
    }
}
