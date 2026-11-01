package com.tengwear.tengstore.ui.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Announcement(
    val title: String,
    val body: String,
    /** 轮播大图 URL；纯文字公告为 null。 */
    val imageUrl: String? = null,
)

/**
 * 公告共享仓库：主菜单轮播、公告页共用同一份数据，
 * 只在首次需要时拉取，避免每个页面重复请求。
 */
object AnnouncementsStore {

    /** null = 尚未加载过；空列表 = 加载成功但无公告。 */
    private val _items = MutableStateFlow<List<Announcement>?>(null)
    val items: StateFlow<List<Announcement>?> = _items.asStateFlow()

    suspend fun load(force: Boolean = false): List<Announcement>? {
        if (!force && _items.value != null) return _items.value
        val repo = AppContainer.settings.parseRepo(AppContainer.settings.announceRepo.value)
            ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                AppContainer.gitHubRepository.rawFile(repo.first, repo.second, AnnouncementsViewModel.ANNOUNCE_FILE)
            }
        }.getOrNull()?.let { text ->
            parseAnnouncements(text, repo.first, repo.second).also { _items.value = it }
        }
    }

    /** 解析 markdown：以 "## " 开头的行为公告标题；节内第一个 ![...](url) 为轮播图，其余为正文。 */
    fun parseAnnouncements(text: String, owner: String, repoName: String): List<Announcement> {
        val imageRegex = Regex("!\\[[^\\]]*]\\(([^)]+)\\)")
        val items = mutableListOf<Announcement>()
        var title: String? = null
        var image: String? = null
        val body = StringBuilder()
        fun flush() {
            title?.let { t ->
                val cleaned = body.toString()
                    .lines()
                    .filter { imageRegex.find(it) == null }
                    .joinToString("\n")
                    .trim()
                items.add(Announcement(t, cleaned, image))
            }
            body.clear()
            image = null
        }
        text.lines().forEach { line ->
            when {
                line.startsWith("## ") -> {
                    flush()
                    title = line.removePrefix("## ").trim()
                }
                title == null -> Unit // 标题前的内容（文件头注释）忽略
                image == null -> imageRegex.find(line)?.let { m ->
                    image = resolveImageUrl(m.groupValues[1].trim(), owner, repoName)
                }
                else -> body.appendLine(line)
            }
        }
        flush()
        return items
    }

    /** 仓库内相对路径转为 raw 地址；完整 URL 原样返回。 */
    private fun resolveImageUrl(url: String, owner: String, repoName: String): String =
        if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://raw.githubusercontent.com/$owner/$repoName/main/${url.removePrefix("/")}"
        }
}

data class AnnouncementsUiState(
    val loading: Boolean = false,
    val items: List<Announcement> = emptyList(),
    val error: String? = null,
)

/**
 * 公告（索引仓库模式）：
 * 公告内容来自公告索引仓库的 announcements.md 文件，
 * 按 `## 标题` 分节，一节即一条公告；节内可附带一张图片作为轮播 banner。
 * 开发者改仓库文件即可更新公告，无需发版。
 */
class AnnouncementsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AnnouncementsUiState())
    val uiState: StateFlow<AnnouncementsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val items = AnnouncementsStore.load(force = true)
            if (items == null) {
                _uiState.value = AnnouncementsUiState(error = "加载失败，请检查网络或公告索引仓库")
            } else {
                _uiState.value = AnnouncementsUiState(items = items)
            }
        }
    }

    companion object {
        /** 公告索引仓库中的公告内容文件。 */
        const val ANNOUNCE_FILE = "announcements.md"
    }
}
