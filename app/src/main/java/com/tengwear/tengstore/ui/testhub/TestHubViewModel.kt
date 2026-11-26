package com.tengwear.tengstore.ui.testhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.DevMailCipher
import com.tengwear.tengstore.data.RepoEntry
import com.tengwear.tengstore.domain.model.Release
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 单个测试项目条目：索引仓库里的一个仓库链接 + 其最新版本信息。 */
data class TestProject(
    val fullName: String,          // owner/repo
    /** 项目开发者邮箱（repos.txt 中加密邮箱解密而来），未附带则为 null。 */
    val devEmail: String? = null,
    /** 自定义显示名（repos.txt `[name:...]`，可中文）；null = 用仓库名。 */
    val displayName: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val latest: Release? = null,   // 最新 Release（含 APK 产物）
    /** 项目介绍：README 内容解析出的段落与图片。 */
    val intro: RepoIntro? = null,
    val introLoading: Boolean = true,
    /** 最新版本比上次看过的版本新（列表显示 🆕 更新表情）。 */
    val hasUpdate: Boolean = false,
) {
    /** 列表 / 详情标题：优先自定义名，其次仓库名。 */
    val title: String get() = displayName ?: fullName.substringAfter("/")
}

data class TestHubUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val hubRepo: String = "",
    val projects: List<TestProject> = emptyList(),
)

/**
 * 测试项目（单开页面）：
 * 应用读取「测试项目索引仓库」（默认 lliiaannggyu/TENC-TestHub）中的 repos.txt，
 * 每行一个仓库链接；这些链接对应的仓库即要测试的项目。
 * 逐个拉取其最新 Release 与 APK 产物，点按即可进入下载安装。
 */
/**
 * 测试列表的进程级缓存：首页「测试」页与「测试项目」页共享同一份数据，
 * 进应用只索引一次；后续进入页面直接用缓存，只有下拉刷新（force=true）才重新索引。
 * 项目的最新版本 / 介绍加载是带缓存的，缓存命中时不再发起网络请求。
 */
object TestHubStore {
    private val _uiState = MutableStateFlow(TestHubUiState())
    val uiState: StateFlow<TestHubUiState> = _uiState.asStateFlow()

    /** 已成功索引过的仓库配置；为空表示还没索引过。 */
    private var indexedRepo: String? = null
    /** 是否已有有效的项目缓存（至少索引出过项目列表）。 */
    private var hasCache: Boolean = false
    /** 正在索引中，避免下拉刷新与页面进入重复触发。 */
    private var indexing: Boolean = false
    /** 已拉取过版本/介绍的项目，命中后不再重复加载。 */
    private val loadedProjects = mutableSetOf<String>()

    /**
     * @param force true = 下拉刷新，强制重新读取索引并刷新每个项目的最新版本/介绍。
     */
    fun load(force: Boolean = false) {
        val settings = AppContainer.settings
        val repoCfg = settings.testHubRepo.value.trim()
        val repo = settings.parseRepo(repoCfg)
        if (repo == null) {
            _uiState.value = TestHubUiState(error = "未配置测试项目仓库，请到设置中指定 owner/repo")
            return
        }
        // 缓存命中：同一仓库、已索引过、非强制刷新 → 直接复用，绝不重新索引
        if (!force && hasCache && indexedRepo == repoCfg && _uiState.value.projects.isNotEmpty()) {
            return
        }
        if (indexing) return
        indexing = true
        _uiState.value = _uiState.value.copy(loading = true, hubRepo = repoCfg, error = null)
        AppContainer.appScope.launch {
            val entries = runCatching {
                withContext(Dispatchers.IO) {
                    val body = AppContainer.gitHubRepository
                        .rawFile(repo.first, repo.second, "repos.txt")
                    TestHubViewModel.parseProjects(body)
                }
            }
            indexing = false
            entries.onFailure { e ->
                AppContainer.log("测试项目", "读取索引失败：${e.message}")
                // 有旧缓存时保留列表，只提示刷新失败
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = if (hasCache) "刷新失败：${e.message?.take(100)}" else "读取索引失败：${e.message?.take(120)}",
                )
            }.onSuccess { list ->
                if (list.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "索引中还没有指定任何测试项目：请在索引仓库的 repos.txt 里添加要测试的项目链接（不是本仓库自身）。",
                    )
                } else {
                    indexedRepo = repoCfg
                    hasCache = true
                    if (force) loadedProjects.clear()
                    // 强制刷新时保留已有数据避免闪空，首次则构建新列表
                    val existing = if (force) _uiState.value.projects.associateBy { it.fullName } else emptyMap()
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = null,
                        projects = list.map { entry ->
                            existing[entry.fullName] ?: TestProject(entry.fullName, entry.devEmail, entry.name)
                        },
                    )
                    list.forEach { loadProject(it.fullName) }
                }
            }
        }
    }

    private fun loadProject(fullName: String) {
        if (!loadedProjects.add(fullName)) return  // 已加载过，跳过（缓存命中）
        val parts = fullName.split("/")
        AppContainer.appScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AppContainer.gitHubRepository.releases(parts[0], parts[1])
                        .firstOrNull { it.assets.any { a -> a.isApk } }
                }
            }
            update(fullName) { project ->
                result.fold(
                    onSuccess = { release ->
                        project.copy(
                            loading = false,
                            latest = release,
                            hasUpdate = release != null &&
                                AppContainer.settings.seenTagOf(fullName) != release.tagName,
                            error = if (release == null) "暂无带 APK 的版本" else null,
                        )
                    },
                    onFailure = { e ->
                        project.copy(loading = false, error = "加载失败：${e.message?.take(60)}")
                    },
                )
            }
            loadIntro(parts[0], parts[1])
        }
    }

    /** 拉取仓库 README 解析为项目介绍（内容 + 图片）。 */
    private fun loadIntro(owner: String, repo: String) {
        AppContainer.appScope.launch {
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
            update("$owner/$repo") { project ->
                project.copy(intro = intro, introLoading = false)
            }
        }
    }

    private fun update(fullName: String, transform: (TestProject) -> TestProject) {
        val current = _uiState.value
        _uiState.value = current.copy(
            projects = current.projects
                .map { if (it.fullName == fullName) transform(it) else it }
                // 最新发布的排在最上面，其余依次递减；没有版本的排最后
                .sortedWith(
                    compareByDescending<TestProject> { it.latest?.publishedAt ?: "" }
                        .thenBy { it.fullName },
                ),
        )
    }

    /** 打开详情即视为看过当前版本：持久化已看 tag 并去掉「有更新」表情。 */
    fun markSeen(fullName: String, tag: String) {
        AppContainer.settings.markSeenTag(fullName, tag)
        val current = _uiState.value
        val target = current.projects.firstOrNull { it.fullName == fullName } ?: return
        if (!target.hasUpdate) return
        _uiState.value = current.copy(
            projects = current.projects.map {
                if (it.fullName == fullName) it.copy(hasUpdate = false) else it
            },
        )
    }

    private val README_CANDIDATES = listOf(
        "README.md", "readme.md", "README_EN.md", "README_zh.md",
        "README.zh-CN.md", "Readme.md",
    )
}

class TestHubViewModel : ViewModel() {
    val uiState: StateFlow<TestHubUiState> = TestHubStore.uiState

    fun load(force: Boolean = false) = TestHubStore.load(force)

    fun markSeen(fullName: String, tag: String) = TestHubStore.markSeen(fullName, tag)

    companion object {
        /**
         * 解析 repos.txt：每行一个 owner/repo 或完整 GitHub 网址，
         * 可附带 `[name:自定义名]` 与 `[dev:<加密开发者邮箱>]` 字段；
         * 忽略空行与 # 注释。
         */
        fun parseProjects(text: String): List<RepoEntry> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { DevMailCipher.parseEntry(it) }
            .distinctBy { it.fullName }
            .toList()
    }
}
