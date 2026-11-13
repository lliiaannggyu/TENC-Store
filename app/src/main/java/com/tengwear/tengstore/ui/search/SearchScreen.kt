package com.tengwear.tengstore.ui.search

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import java.net.URLEncoder
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

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

    val userInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        parseTextInputResult(result.data)?.let { query ->
            val encoded = URLEncoder.encode(query, "UTF-8")
            navController.navigate("user_search/$encoded")
        }
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            autoCentering = null,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("搜索应用") } }

            item {
                GrayButton(
                    onClick = { launchTextInputSafely(inputLauncher, "搜索 GitHub 仓库") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索仓库", maxLines = 1) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            }

            item {
                GrayButton(
                    onClick = { launchTextInputSafely(userInputLauncher, "搜索 GitHub 用户") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("搜索用户", maxLines = 1) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                )
            }

            item {
                GrayButton(
                    onClick = { navController.navigate("settings") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("关键词设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                )
            }
        }
    }
}
