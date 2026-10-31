package com.example.weargithubstore.ui.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Announcement(
    val title: String,
    val body: String,
)

data class AnnouncementsUiState(
    val loading: Boolean = false,
    val items: List<Announcement> = emptyList(),
    val error: String? = null,
)

/**
 * 公告（索引仓库模式）：
 * 公告内容来自公告索引仓库的 announcements.md 文件，
 * 按 `## 标题` 分节，一节即一条公告；开发者改文件即可更新公告，无需发版。
 */
class AnnouncementsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementsUiState())
    val uiState: StateFlow<AnnouncementsUiState> = _uiState.asStateFlow()

    fun load() {
        val repo = AppContainer.settings.parseRepo(AppContainer.settings.announceRepo.value)
        if (repo == null) {
            _uiState.value = AnnouncementsUiState(error = "未配置公告索引仓库，请到设置中指定 owner/repo")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                withContext(Dispatchers.IO) {
                    AppContainer.gitHubRepository.rawFile(repo.first, repo.second, ANNOUNCE_FILE)
                }
            }.onSuccess { text ->
                val items = parseAnnouncements(text)
                AppContainer.log("公告", "加载公告成功，共 ${items.size} 条")
                _uiState.value = AnnouncementsUiState(items = items)
            }.onFailure { e ->
                AppContainer.log("公告", "加载公告失败：${e.message}")
                _uiState.value = AnnouncementsUiState(
                    error = e.message ?: "加载失败，请检查网络或公告索引仓库配置",
                )
            }
        }
    }

    /** 解析 markdown：以 "## " 开头的行为公告标题，直到下一个标题之前都是正文。 */
    private fun parseAnnouncements(text: String): List<Announcement> {
        val items = mutableListOf<Announcement>()
        var title: String? = null
        val body = StringBuilder()
        fun flush() {
            title?.let { t ->
                items.add(Announcement(t, body.toString().trim()))
            }
            body.clear()
        }
        text.lines().forEach { line ->
            if (line.startsWith("## ")) {
                flush()
                title = line.removePrefix("## ").trim()
            } else if (title != null) {
                body.appendLine(line)
            }
        }
        flush()
        return items
    }

    companion object {
        /** 公告索引仓库中的公告内容文件。 */
        const val ANNOUNCE_FILE = "announcements.md"
    }
}
