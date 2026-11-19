package com.tengwear.tengstore.ui.util

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨应用深链：其他应用通过 `tencstore://app/...` 拉起商店并直达对应页面。
 *
 * 支持：
 *  - `tencstore://app/update/{owner~~repo}`    → 该仓库的版本下载页（其他应用「更新」跳这里）
 *  - `tencstore://app/detail/{owner~~repo}`    → 项目详情页
 *  - `tencstore://app/feedback/{owner~~repo}`  → 项目反馈页（其他应用「反馈」跳这里）
 *  - `tencstore://app/auth/{应用名}`            → 授权中心（预填应用名，确认后签发授权码）
 *
 * `{owner~~repo}` 中的 `~~` 是仓库斜杠的占位符（与商店内路由一致）。
 */
object DeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** 预填的授权应用名（来自 auth 深链），供授权中心读取。 */
    val pendingAuthApp = MutableStateFlow<String?>(null)

    fun consume() {
        _pending.value = null
    }

    /** 解析深链为应用内路由；识别成功返回 true。 */
    fun handle(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (uri.scheme != "tencstore") return false
        val segments = uri.pathSegments
        val action = segments.firstOrNull() ?: return false
        val arg = segments.getOrNull(1) ?: ""
        when (action) {
            "update", "download", "releases" -> {
                if (arg.isBlank()) return false
                _pending.value = "releases/$arg"   // arg 保持 owner~~repo（路由斜杠占位）
            }
            "detail" -> {
                if (arg.isBlank()) return false
                _pending.value = "detail/$arg"
            }
            "feedback" -> {
                if (arg.isBlank()) return false
                _pending.value = "feedback/$arg/none"
            }
            "auth" -> {
                pendingAuthApp.value = arg.takeIf { it.isNotBlank() }
                _pending.value = "auth"
            }
            else -> return false
        }
        return true
    }
}
