package com.tengwear.tengstore.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.navigation.NavHostController
import com.tengwear.tengstore.BuildConfig
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用分享助手：
 *  - 分享文本 = 「TENC Store 分享」标记 + 应用 GitHub 链接 + 商店最新版 APK 下载直链；
 *  - 直链由 [BuildConfig.VERSION_NAME] 自动生成（tag = vX.XX，资产名 = TENC-Store-vX.XX.apk），
 *    每次发新版本直链自动跟随，无需手动修改；
 *  - 打开应用时检测剪贴板：命中「TENC Store 分享」标记即提取应用链接并自动跳转。
 */
object ShareHelper {

    /** 分享文本固定标记：剪贴板检测以此识别商店分享内容。 */
    const val SHARE_MARK = "TENC Store 分享"

    /**
     * 本应用最近一次「分享该应用」复制到剪贴板的文本。
     * 焦点检测时若剪贴板内容与之相同，说明是自己刚复制的，直接忽略——
     * 否则会被当成别人的分享而自动跳转并清空剪贴板，导致「复制文本分享」失效。
     */
    private var lastSelfCopiedText: String? = null

    /** 商店最新版 APK 下载直链（随 versionName 自动更新）。 */
    fun storeDownloadLink(): String {
        val v = BuildConfig.VERSION_NAME
        return "https://github.com/lliiaannggyu/TENC-Store/releases/download/v$v/TENC-Store-v$v.apk"
    }

    /** 组装分享文本：应用链接 + 商店下载直链。 */
    fun buildShareText(appFullName: String): String = buildString {
        appendLine("【$SHARE_MARK】")
        appendLine("应用：${appFullName.substringAfter('/')}")
        appendLine("应用链接：https://github.com/$appFullName")
        append("商店下载：${storeDownloadLink()}")
    }

    /**
     * 分享文本（手表适配版）：
     *  - 只做「复制到剪贴板 + Toast」这一件事，不再拉起 ACTION_SEND 分享面板。
     *  - 关键：手表上拉起分享面板会切换窗口焦点，回来时 onWindowFocusChanged 会读到
     *    自己刚复制的文本并误判为别人的分享，触发自动跳转 + 清空剪贴板，
     *    这正是「复制了却粘贴不出来」的根因。
     */
    fun share(context: Context, text: String) {
        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TENC Store share", text))
        }.isSuccess
        if (copied) {
            // 记录自己复制的内容，供焦点检测时排除，避免自产自销式的误跳转/清空。
            lastSelfCopiedText = text
        }
        Toast.makeText(
            context,
            if (copied) "分享文本已复制，去聊天软件粘贴即可" else "复制失败，请重试",
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** 从剪贴板文本提取分享的仓库（owner/repo）；不含分享标记时返回 null。 */
    fun extractSharedRepo(text: String?): String? {
        if (text.isNullOrBlank() || !text.contains(SHARE_MARK)) return null
        val regex = Regex("github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)")
        return regex.find(text)?.groupValues?.get(1)?.trimEnd('.', '/')
    }

    // ---- 剪贴板自动跳转（焦点式读取）----
    // Android 10+ 读剪贴板不再走权限，而是要求应用窗口持有输入焦点；
    // onResume 时机早于获焦，系统会静默返回空。因此在窗口获得焦点后读取。

    private val _pendingJump = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** 待跳转的仓库 fullName（owner/repo），由窗口获焦时的剪贴板检测写入。 */
    val pendingJump: kotlinx.coroutines.flow.StateFlow<String?> = _pendingJump.asStateFlow()

    /**
     * 窗口获得焦点后调用（MainActivity.onWindowFocusChanged）：
     * 读取剪贴板，命中「TENC Store 分享」标记时记下待跳转仓库。
     * 跳转动作由 UI 层在首页消费 [pendingJump] 后执行。
     */
    fun checkClipboardOnFocus(context: Context) {
        if (_pendingJump.value != null) return
        val text = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull() ?: return
        // 关键：若是本应用「分享该应用」刚复制的内容，忽略，绝不触发跳转/清空。
        // 否则自己复制的文本会被自己检测成分享，跳转走且清空剪贴板，粘贴功能失效。
        if (text == lastSelfCopiedText) return
        val repo = extractSharedRepo(text) ?: return
        _pendingJump.value = repo
    }

    /** 跳转完成后清空待跳转记录与剪贴板（避免下次打开重复跳转）。 */
    fun consumeJump(context: Context) {
        _pendingJump.value = null
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            }
        }
    }
}
