package com.tengwear.tengstore.ui.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import java.net.URLEncoder

/**
 * 搜索输入页：只负责发起搜索。
 * 输入完成后跳转独立的搜索结果页（search_results），结果不再挤在本页展示。
 */
@Composable
fun SearchScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val keywordsEnabled by settings.keywordsEnabled.collectAsState()
    val keywords by settings.keywords.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        parseTextInputResult(result.data)?.let { query ->
            val encoded = URLEncoder.encode(query, "UTF-8")
            navController.navigate("search_results/$encoded")
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ListHeader { Text("搜索应用") } }

            item {
                Chip(
                    onClick = { launchTextInputSafely(inputLauncher, "搜索 GitHub 仓库") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索仓库", maxLines = 1) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    colors = ChipDefaults.primaryChipColors(),
                )
            }

            item {
                Chip(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("关键词设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                )
            }
        }
    }
}
