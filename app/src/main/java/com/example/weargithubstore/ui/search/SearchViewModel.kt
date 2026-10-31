package com.example.weargithubstore.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.domain.model.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val loading: Boolean = false,
    val repos: List<Repo> = emptyList(),
    val totalBeforeFilter: Int = 0,
    val error: String? = null,
)

/**
 * 搜索（需求 2、4）：
 * - 关键词开启时，自动把关键词并入搜索条件（GitHub 多词为 AND）；
 * - 关键词开启时，对结果再做一次本地过滤（名称/描述包含任一关键词）。
 */
class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun search(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val settings = AppContainer.settings
        val keywords = keywordsList()
        val useKeywords = settings.keywordsEnabled.value && keywords.isNotEmpty()

        // 需求 4：搜索也使用关键词
        val effectiveQuery = if (useKeywords) "$q ${keywords.joinToString(" ")}" else q

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching { AppContainer.gitHubRepository.search(effectiveQuery) }
                .onSuccess { repos ->
                    // 需求 2：按关键词过滤相关仓库
                    val filtered = if (useKeywords) {
                        repos.filter { matchesKeywords(it, keywords) }
                    } else {
                        repos
                    }
                    AppContainer.log(
                        "搜索",
                        "「$q」结果 ${repos.size} 条，过滤后 ${filtered.size} 条",
                    )
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        repos = filtered,
                        totalBeforeFilter = repos.size,
                    )
                }
                .onFailure { e ->
                    AppContainer.log("搜索", "「$q」失败：${e.message}")
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = e.message ?: "请求失败",
                    )
                }
        }
    }

    private fun keywordsList(): List<String> =
        AppContainer.settings.keywords.value.split(",", "，", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)

    private fun matchesKeywords(repo: Repo, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        val haystack = "${repo.name} ${repo.fullName} ${repo.description ?: ""}".lowercase()
        return keywords.any { haystack.contains(it.lowercase()) }
    }
}
