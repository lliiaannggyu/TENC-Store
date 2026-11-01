package com.tengwear.tengstore.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.BuildConfig
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.download.ApkDownloader
import com.tengwear.tengstore.data.download.DownloadTracker
import com.tengwear.tengstore.data.remote.GitHubClient
import com.tengwear.tengstore.data.remote.dto.toDomain
import com.tengwear.tengstore.domain.model.Asset
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用内更新：检查内置更新源仓库（GitHub Release）的最新版本，
 * 发现新版本后可下载 APK 并安装。
 *
 * 更新完全非强制：只在设置页提供「检查更新」入口，不弹窗、不打断，
 * 用户随时可以滑出页面忽略更新。
 */
class UpdateViewModel : ViewModel() {

    enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, ERROR }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        /** 远端最新版本 tag，如 "v1.19"。 */
        val latestVersion: String = "",
        /** 远端最新 Release 的说明（已精简）。 */
        val releaseNotes: String = "",
        val asset: Asset? = null,
        val error: String = "",
        /** 下载进度百分比；null 表示当前没有正在进行的下载。 */
        val downloadProgress: Int? = null,
        /** 下载完成后的本地文件。 */
        val downloadedFile: File? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** 检查更新源仓库的最新 Release，与当前版本比较。 */
    fun check() {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                phase = Phase.CHECKING,
                error = "",
                downloadedFile = null,
            )
            runCatching {
                val releases = GitHubClient.api
                    .listReleases(UPDATE_REPO_OWNER, UPDATE_REPO_NAME, perPage = 5)
                    .filter { !it.draft }
                val latest = releases.firstOrNull()
                    ?: throw IllegalStateException("更新源还没有 Release")
                val apk = latest.assets.firstOrNull { it.name.endsWith(".apk", true) }
                    ?: throw IllegalStateException("最新 Release 未附带 APK")
                Triple(latest.tagName, apk.toDomain(), cleanNotes(latest.body.orEmpty()))
            }.onSuccess { (tag, apk, notes) ->
                val hasUpdate = compareVersions(parseVersion(tag), parseVersion(BuildConfig.VERSION_NAME)) > 0
                _state.value = _state.value.copy(
                    phase = if (hasUpdate) Phase.AVAILABLE else Phase.UP_TO_DATE,
                    latestVersion = tag,
                    asset = apk,
                    releaseNotes = notes,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    phase = Phase.ERROR,
                    error = e.message ?: "检查失败",
                )
            }
        }
    }

    /** 用应用内流式下载器下载新版本 APK，进度实时写入 state（直连失败自动切镜像）。 */
    fun download() {
        val asset = _state.value.asset ?: return
        if (_state.value.downloadProgress != null) return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _state.value = _state.value.copy(downloadProgress = 0)
            ApkDownloader(AppContainer.context).download(asset) { soFar, total ->
                val progress = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 100) else 0
                DownloadTracker.update(asset.name, progress, soFar, total)
                _state.value = _state.value.copy(downloadProgress = progress)
            }.onSuccess { file ->
                DownloadTracker.clear()
                _state.value = _state.value.copy(downloadProgress = null, downloadedFile = file)
            }.onFailure { e ->
                DownloadTracker.clear()
                _state.value = _state.value.copy(
                    downloadProgress = null,
                    error = "下载失败：${e.message?.take(60) ?: "网络错误"}（已尝试直连与镜像）",
                )
            }
        }
    }

    fun reset() {
        if (downloadJob?.isActive != true) {
            _state.value = UiState()
        }
    }

    private fun cleanNotes(body: String): String = body
        .lines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("![") }
        .joinToString("\n")
        .take(200)
}

/** 应用更新源仓库（本应用自身的开源仓库）。 */
const val UPDATE_REPO_OWNER = "lliiaannggyu"
const val UPDATE_REPO_NAME = "TENC-Store"

/** 解析 "v1.19" / "1.19" 为数字段列表，便于比较。 */
fun parseVersion(tag: String): List<Int> =
    tag.removePrefix("v").removePrefix("V")
        .split(".")
        .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

/** 逐段比较版本号；a > b 返回正数。 */
fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x - y
    }
    return 0
}
