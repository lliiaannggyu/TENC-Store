package com.tengwear.tengstore.ui.assets

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.download.ApkDownloader
import com.tengwear.tengstore.data.download.DownloadTracker
import com.tengwear.tengstore.domain.model.Asset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DownloadUiState {
    data object Idle : DownloadUiState
    data class Downloading(
        val asset: Asset,
        val progress: Int,
        val soFarBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : DownloadUiState
    data class Succeeded(val asset: Asset, val file: File) : DownloadUiState
    data class Failed(val asset: Asset?, val message: String? = null) : DownloadUiState
}

/**
 * Drives a single APK download through the in-app streaming downloader
 * ([ApkDownloader]) and exposes an install action that launches the system
 * package installer via a FileProvider content URI.
 *
 * 进度由应用自己统计（替代系统 DownloadManager 轮询），
 * 直连失败自动切换镜像，进度条实时移动。
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = ApkDownloader(application)
    private val _state = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    fun download(asset: Asset) {
        if (_state.value is DownloadUiState.Downloading) return
        _state.value = DownloadUiState.Downloading(asset, 0)
        DownloadTracker.update(asset.name, 0, 0, asset.size)

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            downloader.download(asset) { soFar, total ->
                val progress = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 100) else 0
                DownloadTracker.update(asset.name, progress, soFar, total)
                _state.value = DownloadUiState.Downloading(asset, progress, soFar, total)
            }.onSuccess { file ->
                DownloadTracker.clear()
                _state.value = DownloadUiState.Succeeded(asset, applyRename(file))
            }.onFailure { e ->
                DownloadTracker.clear()
                _state.value = DownloadUiState.Failed(
                    asset,
                    "下载失败：${e.message?.take(60) ?: "网络错误"}（已尝试直连与镜像）",
                )
            }
        }
    }

    /**
     * 需求 11：按设置中的模板自动重命名文件（选填）。
     * 模板支持 {原名}、{日期}、{时间}，如 "TENC-{原名}" 或 "{日期}-{原名}"。
     */
    private fun applyRename(original: File): File {
        val pattern = AppContainer.settings.renamePattern.value
        if (pattern.isBlank()) return original
        val newName = pattern
            .replace("{原名}", original.name)
            .replace("{日期}", java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA).format(java.util.Date()))
            .replace("{时间}", java.text.SimpleDateFormat("HHmmss", java.util.Locale.CHINA).format(java.util.Date()))
            .ifBlank { original.name }
        if (newName == original.name) return original
        val target = File(original.parentFile, newName)
        if (target.exists()) target.delete()
        return if (original.renameTo(target)) target else original
    }

    fun install(file: File) {
        val context = getApplication<Application>()
        runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, uri).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun reset() {
        if (downloadJob?.isActive != true) {
            _state.value = DownloadUiState.Idle
        }
    }
}
