package com.example.weargithubstore.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.data.LogEntry
import com.example.weargithubstore.data.LogToken
import com.example.weargithubstore.data.MailClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogsUiState(
    val entries: List<LogEntry> = emptyList(),
    val description: String = "",
    val confirmed: Boolean = false,
    val notice: String? = null,
)

/**
 * 日志反馈（需求 7–9、12）：
 * - 自动按固定格式编写日志文件；
 * - 可复制到剪贴板（供 AppOps Pro 等工具读取发送）；
 * - 可通过指定邮箱发送；
 * - 发送前必须填写情况描述并勾选确认。
 */
class LogsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState(entries = AppContainer.logStore.all()))
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    fun refresh() {
        _uiState.value = _uiState.value.copy(entries = AppContainer.logStore.all())
    }

    /** 需求 9：发送前描述情况。 */
    fun setDescription(value: String) {
        _uiState.value = _uiState.value.copy(description = value, notice = null)
    }

    /** 需求 12：勾选后才允许发送。 */
    fun setConfirmed(value: Boolean) {
        _uiState.value = _uiState.value.copy(confirmed = value, notice = null)
    }

    fun clearLogs() {
        AppContainer.logStore.clear()
        AppContainer.log("日志", "日志已清空")
        refresh()
    }

    /** 需求 12：自动使用指定格式编写日志内容。 */
    fun buildReport(): String {
        val state = _uiState.value
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        return buildString {
            appendLine("== TENC Store 日志反馈 ==")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("版本: 1.0 (Wear OS)")
            appendLine("时间: ${timeFmt.format(Date())}")
            appendLine("描述: ${state.description.ifBlank { "（未填写）" }}")
            appendLine("---- 日志 ----")
            if (state.entries.isEmpty()) {
                appendLine("（无日志）")
            } else {
                state.entries.forEach { e ->
                    appendLine("${timeFmt.format(Date(e.time))} [${e.tag}] ${e.message}")
                }
            }
        }
    }

    /** 需求 7：复制到剪贴板（联动 AppOps Pro 等，可粘贴后自动发送）。 */
    fun copyToClipboard(context: Context): Boolean {
        if (!canSend()) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TENC Store 日志", buildReport()))
        AppContainer.log("日志", "日志已复制到剪贴板")
        _uiState.value = _uiState.value.copy(notice = "已复制到剪贴板")
        return true
    }

    /**
     * 需求 8：邮箱托管——像邮箱软件一样在应用内直接发邮件：
     * 用用户自己的邮箱 + SMTP 授权码（SMTP 发送），把日志发给开发者邮箱。
     * 不依赖系统邮件应用（手表上通常没有）。需要邮箱已验证。
     */
    fun sendByEmail(context: Context) {
        if (!canSend()) return
        val settings = AppContainer.settings
        val email = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        when {
            email.isBlank() -> {
                _uiState.value = _uiState.value.copy(notice = "请先在设置中填写开发者邮箱")
            }
            authCode.isBlank() -> {
                _uiState.value = _uiState.value.copy(notice = "请先在设置中填写 SMTP 授权码")
            }
            !settings.developerEmailVerified.value -> {
                _uiState.value = _uiState.value.copy(notice = "开发者邮箱未验证，请先发送验证邮件")
            }
            else -> {
                _uiState.value = _uiState.value.copy(notice = "正在通过邮箱托管发送…")
                viewModelScope.launch {
                    // 自动在邮件里附加日志口令：内置密码本加密的发送时间，
                    // 开发者端「信息」板块解密校验后才展示这封邮件。
                    val token = LogToken.generate()
                    val err = MailClient.send(
                        from = email,
                        authCode = authCode,
                        to = email,
                        subject = "TENC Store 日志反馈",
                        body = buildReport() + "\n\n" + token,
                    )
                    if (err == null) {
                        AppContainer.log("日志", "日志已通过邮箱托管发送至 $email（含日志口令）")
                        _uiState.value = _uiState.value.copy(notice = "日志已发送到开发者邮箱")
                    } else {
                        AppContainer.log("日志", "邮箱托管发送失败：$err")
                        _uiState.value = _uiState.value.copy(notice = "发送失败：${err.take(80)}")
                    }
                }
            }
        }
    }

    /** 需求 9 + 12：必须已描述情况并勾选确认。 */
    fun canSend(): Boolean {
        val state = _uiState.value
        if (state.description.isBlank()) {
            _uiState.value = state.copy(notice = "发送前请先描述情况")
            return false
        }
        if (!state.confirmed) {
            _uiState.value = state.copy(notice = "请先勾选确认后再发送")
            return false
        }
        return true
    }
}
