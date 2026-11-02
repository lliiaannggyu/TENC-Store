package com.tengwear.tengstore.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

data class RandomDrawState(
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * 随机抽取 GitHub 项目：
 * - 随机两个字母做仓库名关键词，随机页码取一批候选；
 * - 排除已抽过的（本地持久化 drawnRepos）；
 * - 逐个查 Releases，只认带 APK 产物的项目，命中即回跳详情。
 */
class RandomDrawViewModel : ViewModel() {
    private val _state = MutableStateFlow(RandomDrawState())
    val state: StateFlow<RandomDrawState> = _state.asStateFlow()

    /** onResult 回调命中仓库的 fullName（调用方负责跳转详情）。 */
    fun draw(onResult: (String) -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val drawn = AppContainer.settings.drawnRepos.value
                val hit = findWithApk(drawn)
                if (hit == null) {
                    _state.value = _state.value.copy(busy = false, error = "这轮没抽到新的含 APK 项目，再试试")
                } else {
                    AppContainer.settings.addDrawnRepo(hit)
                    AppContainer.log("随机抽取", "抽中 $hit")
                    _state.value = _state.value.copy(busy = false)
                    onResult(hit)
                }
            } catch (e: Exception) {
                AppContainer.log("随机抽取", "失败：${e.message}")
                _state.value = _state.value.copy(busy = false, error = e.message ?: "抽取失败")
            }
        }
    }

    private suspend fun findWithApk(exclude: Set<String>): String? {
        val letters = "abcdefghijklmnopqrstuvwxyz"
        repeat(6) { round ->
            val q = "${letters[Random.nextInt(26)]}${letters[Random.nextInt(26)]} in:name"
            val page = Random.nextInt(1, 6)
            val repos = runCatching {
                AppContainer.gitHubRepository.search(q, page = page, perPage = 50)
            }.getOrDefault(emptyList())
            for (repo in repos.filter { it.fullName !in exclude }.shuffled().take(10)) {
                val owner = repo.fullName.substringBefore("/")
                val name = repo.fullName.substringAfter("/")
                val hasApk = runCatching {
                    AppContainer.gitHubRepository.releases(owner, name)
                        .any { rel -> rel.assets.any { it.isApk } }
                }.getOrDefault(false)
                if (hasApk) return repo.fullName
            }
            kotlinx.coroutines.delay(300)
        }
        return null
    }
}
