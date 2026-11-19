package com.tengwear.tengstore.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.navigation.NavHostController
import com.tengwear.tengstore.BuildConfig

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
     *  - 先把分享文本复制到剪贴板并 Toast 提示（手表上最稳的方式）；
     *  - 若系统存在分享面板（ACTION_SEND 处理者），再尝试拉起，失败静默降级。
     */
    fun share(context: Context, text: String) {
        // 1. 复制到剪贴板（前台应用可写剪贴板，无需权限）
        val copied = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TENC Store share", text))
        }.isSuccess
        Toast.makeText(
            context,
            if (copied) "分享文本已复制，去聊天软件粘贴即可" else "复制失败，请重试",
            Toast.LENGTH_SHORT,
        ).show()
        // 2. 尝试拉起系统分享面板（手表上多数没有，失败不影响复制结果）
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "分享应用"))
        }
    }

    /** 从剪贴板文本提取分享的仓库（owner/repo）；不含分享标记时返回 null。 */
    fun extractSharedRepo(text: String?): String? {
        if (text.isNullOrBlank() || !text.contains(SHARE_MARK)) return null
        val regex = Regex("github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)")
        return regex.find(text)?.groupValues?.get(1)?.trimEnd('.', '/')
    }

    /**
     * 回到前台时检测剪贴板：有分享内容且当前在首页，自动跳到对应项目页并清除剪贴板，
     * 避免重复跳转。
     */
    fun handleClipboardOnResume(context: Context, nav: NavHostController) {
        if (nav.currentDestination?.route != "home") return
        val text = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString()
        }.getOrNull() ?: return
        val repo = extractSharedRepo(text) ?: return
        runCatching { nav.navigate("detail/${repo.replace("/", "~~")}") }
        runCatching {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .clearPrimaryClip()
        }
    }
}
