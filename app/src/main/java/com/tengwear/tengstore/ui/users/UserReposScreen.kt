package com.tengwear.tengstore.ui.users

import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Repo
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.tengwear.tengstore.ui.util.edgeSpacers

data class UserReposUiState(
    val loading: Boolean = false,
    val repos: List<Repo> = emptyList(),
    val error: String? = null,
)

/** 用户仓库：/users/{login}/repos，点任意仓库进入详情下载。 */
class UserReposViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UserReposUiState())
    val uiState: StateFlow<UserReposUiState> = _uiState.asStateFlow()

    fun load(user: String) {
        if (user.isBlank() || _uiState.value.loading || _uiState.value.repos.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { AppContainer.gitHubRepository.userRepos(user) }
                .onSuccess { repos ->
                    AppContainer.log("用户仓库", "$user 共 ${repos.size} 个仓库")
                    _uiState.value = _uiState.value.copy(loading = false, repos = repos)
                }
                .onFailure { e ->
                    AppContainer.log("用户仓库", "$user 加载失败：${e.message}")
                    _uiState.value = _uiState.value.copy(
                        loading = false, error = e.message ?: "请求失败",
                    )
                }
        }
    }
}

/** 某用户的仓库列表：点击仓库 → 复用现有详情页（查看版本 / 下载 APK）。 */
@Composable
fun UserReposScreen(navController: NavHostController, user: String) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<UserReposViewModel>()
    val state by viewModel.uiState.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    LaunchedEffect(user) { viewModel.load(user) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("$user 的仓库") } }

            if (state.loading) {
                item { Text("加载中…", modifier = Modifier.padding(8.dp)) }
            }
            state.error?.let { err ->
                item { Text("失败：$err", modifier = Modifier.padding(8.dp)) }
            }
            if (!state.loading && state.error == null && state.repos.isEmpty()) {
                item { Text("该用户暂无公开仓库", modifier = Modifier.padding(8.dp)) }
            }

            items(state.repos) { repo ->
                Button(
                    onClick = {
                        navController.navigate("detail/${repo.fullName.replace("/", "~~")}")
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = {
                        Text(
                            if (repo.description.isNullOrBlank()) repo.name
                            else "${repo.name} · ${repo.description}",
                            maxLines = 2,
                        )
                    },
                    icon = {
                        com.tengwear.tengstore.ui.util.RepoIcon(repo.fullName, Modifier.size(22.dp))
                    },
                )
            }
        }
    }
}
