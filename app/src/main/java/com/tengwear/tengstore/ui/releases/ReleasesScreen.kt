package com.tengwear.tengstore.ui.releases

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.items
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Release

@Composable
fun ReleasesScreen(navController: NavHostController, fullName: String) {
    val viewModel: ReleasesViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(fullName) { viewModel.load(fullName) }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("版本发布") } }
            item {
                Text(fullName, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }

            if (uiState.loading) {
                item { Chip(onClick = {}, enabled = false, label = { Text("加载中…") }) }
            }

            uiState.error?.let { err ->
                item { Text("出错：$err", modifier = Modifier.padding(8.dp)) }
            }

            if (!uiState.loading && uiState.error == null) {
                if (uiState.releases.isEmpty()) {
                    item { Text("没有可用的发布版本", modifier = Modifier.padding(8.dp)) }
                } else {
                    items(uiState.releases.size) { i ->
                        val release = uiState.releases[i]
                        ReleaseChip(release = release) {
                            AppContainer.releaseCache[release.id] = release
                            navController.navigate("assets/${release.id}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseChip(release: Release, onClick: () -> Unit) {
    val apkCount = release.assets.count { it.isApk }
    Chip(
        onClick = onClick,
        enabled = apkCount > 0,
        label = { Text(release.tagName, maxLines = 1) },
        secondaryLabel = {
            Text(
                buildString {
                    append(if (release.prerelease) "预发布" else "正式版")
                    if (apkCount > 0) append(" · $apkCount 个 APK")
                },
                maxLines = 1,
            )
        },
        icon = { Icon(Icons.Default.Android, contentDescription = null) },
    )
}
