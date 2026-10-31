package com.tengwear.tengstore.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局下载进度跟踪：下载中的任务状态跨页面共享，
 * 「我的下载」页据此展示条形进度条。
 */
object DownloadTracker {

    data class Active(
        val name: String,
        val progress: Int,      // 0..100
        val soFar: Long,        // 已下载字节
        val total: Long,        // 总字节（未知为 0）
    )

    private val _active = MutableStateFlow<Active?>(null)
    val active: StateFlow<Active?> = _active.asStateFlow()

    fun update(name: String, progress: Int, soFar: Long, total: Long) {
        _active.value = Active(name, progress.coerceIn(0, 100), soFar, total)
    }

    fun clear() {
        _active.value = null
    }
}
