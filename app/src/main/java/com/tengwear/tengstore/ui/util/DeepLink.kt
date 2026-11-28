package com.tengwear.tengstore.ui.util

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨应用深链：其他应用通过 `tencstore://app/...` 拉起商店并直达对应页面。
 *
 * 支持（授权协议 v2）：
 *  - `tencstore://app/update/{owner~~repo}`    → 该仓库的版本下载页（其他应用「更新」跳这里）
 *  - `tencstore://app/detail/{owner~~repo}`    → 项目详情页
 *  - `tencstore://app/feedback/{owner~~repo}`  → 项目反馈页（其他应用「反馈」跳这里）
 *  - `tencstore://app/auth/{应用名}?n={nonce}&d={设备指纹}&cb={回调URI}`
 *      → 授权中心（通用授权请求，任意本机应用均可发起）：
 *      · `{应用名}`：请求方自声明的应用标识（任意非空字符串，trim 后使用）；
 *      · `n`：请求方一次性随机数（hex，防重放，可选）；
 *      · `d`：请求方设备指纹（如 ANDROID_ID，绑定授权码到设备，可选）；
 *      · `cb`：请求方回调 URI（发起方已 URL 编码，getQueryParameter 自动解码，
 *        商店不校验、不限定 scheme，可选）。
 *      三个参数都可能缺省，兼容最简请求 `tencstore://app/auth/{应用名}`。
 *
 * `{owner~~repo}` 中的 `~~` 是仓库斜杠的占位符（与商店内路由一致）。
 */
object DeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** 预填的授权应用名（来自 auth 深链），供授权中心读取，消费后置 null。 */
    val pendingAuthApp = MutableStateFlow<String?>(null)

    /** 授权请求的一次性随机数（n，可选，防重放）。 */
    val pendingAuthNonce = MutableStateFlow<String?>(null)

    /** 授权请求的设备指纹（d，可选，绑定授权码到设备）。 */
    val pendingAuthDevice = MutableStateFlow<String?>(null)

    /** 授权请求的回调 URI（cb，可选，签发后回跳请求方应用直传授权码）。 */
    val pendingAuthCallback = MutableStateFlow<String?>(null)

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
                // 协议 v2：可选参数全部由发起方通过 query 传入，缺省为 null（兼容旧版最简请求）
                pendingAuthNonce.value = uri.getQueryParameter("n")?.takeIf { it.isNotBlank() }
                pendingAuthDevice.value = uri.getQueryParameter("d")?.takeIf { it.isNotBlank() }
                pendingAuthCallback.value = uri.getQueryParameter("cb")?.takeIf { it.isNotBlank() }
                _pending.value = "auth"
            }
            else -> return false
        }
        return true
    }
}
