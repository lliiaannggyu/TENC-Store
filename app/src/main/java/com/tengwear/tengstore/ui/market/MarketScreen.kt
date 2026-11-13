package com.tengwear.tengstore.ui.market

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shop
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Release
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.formatSize
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

/** GitHub 市场页：全部应用直接可见可下载，测试版带「内测」角标。 */
@Composable
fun MarketScreen(navController: NavHostController) {
    val viewModel: MarketViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(Unit) { viewModel.load() }

    fun openRelease(release: Release) {
        AppContainer.releaseCache[release.id] = release
        navController.navigate("assets/${release.id}")
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
        edgeSpacers()
            item { ListHeader { Text("GitHub 市场") } }

            if (uiState.loading) {
                item { GrayButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT), label = { Text("加载中…") }) }
            }
            uiState.error?.let { err ->
                item { Text(err, modifier = Modifier.padding(8.dp)) }
            }
            if (!uiState.loading && uiState.error == null && uiState.apps.isEmpty()) {
                item { Text("市场暂无应用。", modifier = Modifier.padding(8.dp)) }
            }

            items(uiState.apps.size) { i ->
                val app = uiState.apps[i]
                MarketChip(app = app) { openRelease(app.release) }
            }

            item {
                GrayButton(
                    onClick = { viewModel.load() },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("刷新货架", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun MarketChip(app: MarketApp, onClick: () -> Unit) {
    val release = app.release
    val apks = release.assets.filter { it.isApk }
    GrayButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
        enabled = apks.isNotEmpty(),
        label = {
            Text(
                (if (app.isTest) "内测 " else "") + (release.name ?: release.tagName),
                maxLines = 1,
            )
        },
        secondaryLabel = {
            Text(
                buildString {
                    append(release.tagName)
                    if (apks.isNotEmpty()) {
                        append(" · ${formatSize(apks.sumOf { it.size })}")
                    }
                },
                maxLines = 1,
            )
        },
        icon = { Icon(Icons.Default.Android, contentDescription = null) },
        colors = if (app.isTest) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
    )
}
