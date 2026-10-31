package com.example.weargithubstore.ui.assets

import android.app.Application
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.data.download.ApkDownloader
import com.example.weargithubstore.data.download.DownloadTracker
import com.example.weargithubstore.domain.model.Asset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
 * Drives a single APK download: enqueues it via [ApkDownloader], polls progress
 * through [DownloadManager], and exposes an install action that launches the
 * system package installer via a FileProvider content URI.
 *
 * 闪退修复：
 * - Android 14+（targetSdk 34）要求动态注册广播接收器必须指定 RECEIVER_EXPORTED /
 *   RECEIVER_NOT_EXPORTED，否则 registerReceiver 直接抛 SecurityException——此前
 *   点下载即崩溃而系统下载继续进行，正是这个原因；
 * - 注册 / 入队 / 注销全部 runCatching 防御，任何异常都以 Failed 状态呈现而非崩溃。
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = ApkDownloader(application)
    private val _state = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var receiver: BroadcastReceiver? = null

    fun download(asset: Asset) {
        if (_state.value is DownloadUiState.Downloading) return
        _state.value = DownloadUiState.Downloading(asset, 0)

        val context = getApplication<Application>()
        val downloadId = try {
            downloader.enqueue(asset)
        } catch (e: Exception) {
            _state.value = DownloadUiState.Failed(asset, "无法开始下载：${e.message?.take(80)}")
            return
        }

        try {
            val r = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) handleComplete(asset)
                }
            }
            receiver = r
            ContextCompat.registerReceiver(
                context,
                r,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (e: Exception) {
            // 注册失败不致命：仍有轮询兜底
            receiver = null
        }

        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var lastProgress = 0
            while (isActive) {
                val snapshot = runCatching { downloader.querySnapshot(downloadId) }.getOrNull()
                when {
                    snapshot == null -> delay(500)
                    snapshot.done -> {
                        DownloadTracker.clear()
                        if (snapshot.failed) {
                            _state.value = DownloadUiState.Failed(asset, "下载失败（系统下载器返回错误）")
                        } else {
                            handleComplete(asset)
                        }
                        break
                    }
                    else -> {
                        lastProgress = snapshot.progress ?: lastProgress
                        DownloadTracker.update(asset.name, lastProgress, snapshot.soFar, snapshot.total)
                        _state.value = DownloadUiState.Downloading(
                            asset = asset,
                            progress = lastProgress,
                            soFarBytes = snapshot.soFar,
                            totalBytes = snapshot.total,
                        )
                        delay(400)
                    }
                }
            }
        }
    }

    private fun handleComplete(asset: Asset) {
        pollJob?.cancel()
        DownloadTracker.clear()
        val file = downloader.getFile(asset)
        _state.value = if (file != null) {
            DownloadUiState.Succeeded(asset, applyRename(file))
        } else {
            DownloadUiState.Failed(asset, "下载完成后未找到文件")
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
        _state.value = DownloadUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        runCatching {
            receiver?.let { getApplication<Application>().unregisterReceiver(it) }
        }
        receiver = null
    }
}
