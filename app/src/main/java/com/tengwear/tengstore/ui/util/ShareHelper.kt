package com.tengwear.tengstore.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.tengwear.tengstore.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用分享助手：
 *  - 分享文本 = 「TENC Store 分享」标记 + 应用 GitHub 链接 + 商店最新版 APK 下载直链；
 *  - 直链由 [BuildConfig.VERSION_NAME] 自动生成（tag = vX.XX，资产名 = TENC-Store-vX.XX.apk），
 *    每次发新版本直链自动跟随，无需手动修改；
 *  - 打开应用时检测剪贴板：命中「TENC Store 分享」标记即提取应用链接并自动跳转。
 *
 *  为什么授权码能复制、分享曾经"不能复制"：
 *   - 复制动作本身两者完全一样，都是 ClipboardManager.setPrimaryClip；
 *   - 分享文本额外带 SHARE_MARK，会被本应用的「剪贴板自动跳转」盯上。旧逻辑在跳转后
 *     直接 clearPrimaryClip，把用户刚复制、正要去聊天软件粘贴的文本清掉，表现为"分享复制不了"；
 *   - 现在跳转后不再清空剪贴板，改用持久化的「已消费文本」去重，避免重复跳转；
 *   - 「自己复制的文本」也持久化保存，进程被系统回收后仍能识别，不会误判成别人的分享。
 */
object ShareHelper {

    /** 分享文本固定标记：剪贴板检测以此识别商店分享内容。 */
    const val SHARE_MARK = "TENC Store 分享"

    private const val PREF_NAME = "tenc_store_share"
    private const val KEY_SELF_COPIED = "self_copied_text"
    private const val KEY_CONSUMED = "consumed_text"

    /** 内存快速路径：本应用最近一次复制的分享文本。 */
    private var lastSelfCopiedText: String? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** 商店最新版 APK 下载直链（随 versionName 自动更新）。 */
    fun storeDownloadLink(): String {
        val v = BuildConfig.VERSION_NAME
        return "https://github.com/lliiaannggyu/TENC-Store/releases/download/v$v/TENC-Store-v$v.apk"
    }

    /** 组装分享文本：应用链接 + 商店下载直链。全部为本地字符串拼接，不依赖网络。 */
    fun buildShareText(appFullName: String): String = buildString {
        appendLine("【$SHARE_MARK】")
        appendLine("应用：${appFullName.substringAfter('/')}")
        appendLine("应用链接：https://github.com/$appFullName")
        append("商店下载：${storeDownloadLink()}")
    }

    /**
     * 分享文本（手表适配版）：只做「复制到剪贴板 + Toast」这一件事。
     * 复制成功后把文本持久化记为「自己复制的」，供焦点检测排除，避免误跳转/清空。
     */
    fun share(context: Context, text: String) {
        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TENC Store share", text))
        }.isSuccess
        if (copied) {
            lastSelfCopiedText = text
            runCatching { prefs(context).edit().putString(KEY_SELF_COPIED, text).apply() }
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

    private val _pendingJump = MutableStateFlow<String?>(null)

    /** 待跳转的仓库 fullName（owner/repo），由窗口获焦时的剪贴板检测写入。 */
    val pendingJump: StateFlow<String?> = _pendingJump.asStateFlow()

    /**
     * 窗口获得焦点后调用（MainActivity.onWindowFocusChanged）：
     * 读取剪贴板，命中「TENC Store 分享」标记时记下待跳转仓库。
     *  - 自己复制的文本（含持久化兜底）：忽略，保证能粘贴；
     *  - 已消费过的分享文本：忽略，避免重复跳转。
     */
    fun checkClipboardOnFocus(context: Context) {
        if (_pendingJump.value != null) return
        val text = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull() ?: return
        val p = prefs(context)
        val selfCopied = lastSelfCopiedText ?: p.getString(KEY_SELF_COPIED, null)
        if (text == selfCopied) return
        if (text == p.getString(KEY_CONSUMED, null)) return
        val repo = extractSharedRepo(text) ?: return
        _pendingJump.value = repo
    }

    /**
     * 跳转完成后调用：清空待跳转记录，并把该分享文本记为「已消费」以防重复跳转。
     * 注意：绝不 clearPrimaryClip——清空剪贴板正是早期「分享复制不了」的根因；
     * 用户复制后要去聊天软件粘贴，剪贴板必须保留内容。
     */
    fun consumeJump(context: Context) {
        _pendingJump.value = null
        val text = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull()
        if (text != null) {
            runCatching { prefs(context).edit().putString(KEY_CONSUMED, text).apply() }
        }
    }
}
