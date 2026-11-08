package com.tengwear.tengstore.ui.releases

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Release
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

@Composable
fun ReleasesScreen(navController: NavHostController, fullName: String) {
    val viewModel: ReleasesViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(fullName) { viewModel.load(fullName) }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("版本发布") } }
            item {
                Text(fullName, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }

            if (uiState.loading) {
                item { GrayButton(onClick = {}, enabled = false, label = { Text("加载中…") }) }
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
    GrayButton(
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
