package com.example.weargithubstore.ui.testhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.data.DevMailCipher
import com.example.weargithubstore.domain.model.Release
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
    val loading: Boolean = true,
    val error: String? = null,
    val latest: Release? = null,   // 最新 Release（含 APK 产物）
    /** 项目介绍：README 内容解析出的段落与图片。 */
    val intro: RepoIntro? = null,
    val introLoading: Boolean = true,
)

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
class TestHubViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TestHubUiState())
    val uiState: StateFlow<TestHubUiState> = _uiState.asStateFlow()

    fun load() {
        val settings = AppContainer.settings
        val repo = settings.parseRepo(settings.testHubRepo.value)
        if (repo == null) {
            _uiState.value = TestHubUiState(error = "未配置测试项目仓库，请到设置中指定 owner/repo")
            return
        }
        _uiState.value = TestHubUiState(loading = true, hubRepo = settings.testHubRepo.value)
        viewModelScope.launch {
            val entries = runCatching {
                withContext(Dispatchers.IO) {
                    val body = AppContainer.gitHubRepository
                        .rawFile(repo.first, repo.second, "repos.txt")
                    parseProjects(body)
                }
            }
            entries.onFailure { e ->
                AppContainer.log("测试项目", "读取索引失败：${e.message}")
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "读取索引失败：${e.message?.take(120)}",
                )
            }.onSuccess { list ->
                if (list.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = "索引中还没有指定任何测试项目：请在索引仓库的 repos.txt 里添加要测试的项目链接（不是本仓库自身）。",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        projects = list.map { TestProject(it.first, it.second) },
                    )
                    list.forEach { (fullName, _) -> loadProject(fullName) }
                }
            }
        }
    }

    private fun loadProject(fullName: String) {
        val parts = fullName.split("/")
        viewModelScope.launch {
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
            update("$owner/$repo") { project ->
                project.copy(intro = intro, introLoading = false)
            }
        }
    }

    private fun update(fullName: String, transform: (TestProject) -> TestProject) {
        val current = _uiState.value
        _uiState.value = current.copy(
            projects = current.projects.map { if (it.fullName == fullName) transform(it) else it },
        )
    }

    companion object {
        /** README 常见文件名，按顺序尝试。 */
        private val README_CANDIDATES = listOf(
            "README.md", "readme.md", "README_EN.md", "README_zh.md",
            "README.zh-CN.md", "Readme.md",
        )

        /**
         * 解析 repos.txt：每行一个 owner/repo 或完整 GitHub 网址，
         * 可附带 `[dev:<加密开发者邮箱>]` 字段；忽略空行与 # 注释。
         * @return (仓库 fullName, 解密后的开发者邮箱或 null)
         */
        fun parseProjects(text: String): List<Pair<String, String?>> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { DevMailCipher.parseRepo(it) }
            .distinctBy { it.first }
            .toList()
    }
}
