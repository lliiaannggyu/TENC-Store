package com.example.weargithubstore.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.domain.model.Release
import com.example.weargithubstore.ui.testhub.RepoIntro
import com.example.weargithubstore.ui.testhub.RepoIntroParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RepoDetailUiState(
    val fullName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val latest: Release? = null,
    val intro: RepoIntro? = null,
    val introLoading: Boolean = true,
)

/**
 * 仓库详情（搜索结果 / 收藏 / 历史记录点入）：
 * - 拉取最新 Release（版本、包名、体积）；
 * - 拉取 README 解析为文字说明 + 配图（与测试项目详情一致）；
 * - 打开即写入浏览历史；可收藏 / 取消收藏。
 */
class RepoDetailViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RepoDetailUiState())
    val uiState: StateFlow<RepoDetailUiState> = _uiState.asStateFlow()

    fun init(fullName: String) {
        if (_uiState.value.fullName == fullName) return
        _uiState.value = RepoDetailUiState(fullName = fullName)
        AppContainer.settings.addHistory(fullName)
        loadRelease(fullName)
        loadIntro(fullName)
    }

    private fun loadRelease(fullName: String) {
        val (owner, repo) = fullName.split("/", limit = 2).let { it[0] to it[1] }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AppContainer.gitHubRepository.releases(owner, repo)
                        .firstOrNull { it.assets.any { a -> a.isApk } }
                }
            }
            _uiState.value = result.fold(
                onSuccess = { release ->
                    _uiState.value.copy(
                        loading = false,
                        latest = release,
                        error = if (release == null) "暂无带 APK 的版本" else null,
                    )
                },
                onFailure = { e ->
                    _uiState.value.copy(loading = false, error = "加载失败：${e.message?.take(80)}")
                },
            )
        }
    }

    private fun loadIntro(fullName: String) {
        val (owner, repo) = fullName.split("/", limit = 2).let { it[0] to it[1] }
        viewModelScope.launch {
            val intro = runCatching {
                withContext(Dispatchers.IO) {
                    val md = README_CANDIDATES.firstNotNullOfOrNull { path ->
                        runCatching {
                            AppContainer.gitHubRepository.rawFile(owner, repo, path)
                        }.getOrNull()?.takeIf { it.isNotBlank() }
                    }
                    md?.let { RepoIntroParser.parse(owner, repo, it) }
                }
            }.getOrNull()
            _uiState.value = _uiState.value.copy(intro = intro, introLoading = false)
        }
    }

    private companion object {
        val README_CANDIDATES = listOf(
            "README.md", "readme.md", "README_EN.md", "README_zh.md",
            "README.zh-CN.md", "Readme.md",
        )
    }
}
