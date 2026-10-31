package com.tengwear.tengstore.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.TestCode
import com.tengwear.tengstore.domain.model.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MarketApp(
    val release: Release,
    /** 预发布 / tag 带 beta·alpha·test·内测 标记，仅用于角标展示，不影响下载。 */
    val isTest: Boolean,
)

data class MarketUiState(
    val loading: Boolean = false,
    val apps: List<MarketApp> = emptyList(),
    val error: String? = null,
)

/**
 * GitHub 市场（服务端推送应用）：从市场仓库读取 Release 作为应用货架。
 * 所有应用（含测试版）直接可见可下载，无需口令，不再做任何解锁校验；
 * 测试版仅带「内测」角标以示区别。
 */
class MarketViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    fun load() {
        val repo = AppContainer.settings.parseRepo(AppContainer.settings.marketRepo.value)
            ?: AppContainer.settings.parseRepo(AppContainer.settings.pushRepo.value)
        if (repo == null) {
            _uiState.value = MarketUiState(error = "未配置市场仓库，请到设置中指定 owner/repo")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    AppContainer.gitHubRepository.releases(repo.first, repo.second)
                }
            }.onSuccess { list ->
                val apps = list.map { MarketApp(it, TestCode.isTestRelease(it.tagName, it.prerelease)) }
                AppContainer.log("市场", "货架刷新：共 ${apps.size} 个应用")
                _uiState.value = MarketUiState(apps = apps)
            }.onFailure { e ->
                AppContainer.log("市场", "货架刷新失败：${e.message}")
                _uiState.value = MarketUiState(error = e.message ?: "加载失败")
            }
        }
    }
}
