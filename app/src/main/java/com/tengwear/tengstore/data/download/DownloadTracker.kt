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

    /** 最近一次下载结束的通知（成功/失败），首页提示条展示后由界面消费。 */
    data class Finished(val name: String, val success: Boolean, val at: Long = System.currentTimeMillis())

    private val _finished = MutableStateFlow<Finished?>(null)
    val finished: StateFlow<Finished?> = _finished.asStateFlow()

    fun update(name: String, progress: Int, soFar: Long, total: Long) {
        _active.value = Active(name, progress.coerceIn(0, 100), soFar, total)
    }

    fun finish(name: String) {
        _active.value = null
        _finished.value = Finished(name, success = true)
    }

    fun finishFailed(name: String) {
        _active.value = null
        _finished.value = Finished(name, success = false)
    }

    fun clear() {
        _active.value = null
    }

    /** 首页提示条展示后清除通知。 */
    fun consumeFinished() {
        _finished.value = null
    }
}
