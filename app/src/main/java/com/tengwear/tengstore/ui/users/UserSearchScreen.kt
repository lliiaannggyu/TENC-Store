package com.tengwear.tengstore.ui.users

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton

data class UserSearchUiState(
    val loading: Boolean = false,
    val users: List<String> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
)

/** 用户搜索：GitHub /search/users，返回用户名列表。 */
class UserSearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UserSearchUiState())
    val uiState: StateFlow<UserSearchUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { AppContainer.gitHubRepository.searchUsers(q) }
                .onSuccess { users ->
                    AppContainer.log("搜索用户", "「$q」找到 ${users.size} 位用户")
                    _uiState.value = _uiState.value.copy(
                        loading = false, users = users, total = users.size,
                    )
                }
                .onFailure { e ->
                    AppContainer.log("搜索用户", "「$q」失败：${e.message}")
                    _uiState.value = _uiState.value.copy(
                        loading = false, error = e.message ?: "请求失败",
                    )
                }
        }
    }
}

/** 用户搜索结果页：列出匹配的用户，点进该用户的仓库列表。 */
@Composable
fun UserSearchScreen(navController: NavHostController, query: String) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<UserSearchViewModel>()
    val state by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(query) { viewModel.search(query) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("用户「$query」") } }

            if (state.loading) {
                item { Text("搜索中…", modifier = Modifier.padding(8.dp)) }
            }
            state.error?.let { err ->
                item { Text("失败：$err", modifier = Modifier.padding(8.dp)) }
            }
            if (!state.loading && state.error == null && state.users.isEmpty()) {
                item { Text("未找到匹配用户", modifier = Modifier.padding(8.dp)) }
            }
            if (state.users.isNotEmpty()) {
                item { Text("共 ${state.users.size} 位用户", modifier = Modifier.padding(8.dp)) }
            }

            items(state.users) { user ->
                val favUsers by AppContainer.settings.favoriteUsers.collectAsState()
                GrayButton(
                    onClick = {
                        navController.navigate("user_repos/${java.net.URLEncoder.encode(user, "UTF-8")}")
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text(user, maxLines = 1) },
                    icon = {
                        Icon(
                            if (user in favUsers) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (user in favUsers) "已收藏" else "未收藏",
                        )
                    },
                )
            }
        }
    }
}
