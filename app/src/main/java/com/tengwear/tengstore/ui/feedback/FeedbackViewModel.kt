package com.tengwear.tengstore.ui.feedback

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.LogToken
import com.tengwear.tengstore.data.MailClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 反馈类型：建议 / Bug。 */
enum class FeedbackType(val label: String) {
    SUGGESTION("建议"),
    BUG("Bug"),
}

data class FeedbackUiState(
    val fullName: String = "",
    val devEmail: String = "",
    val type: FeedbackType = FeedbackType.BUG,
    val content: String = "",
    val busy: Boolean = false,
    val notice: String? = null,
)

/**
 * 项目反馈（属于「测试项目」板块，用于项目测试）：
 * - 反馈分为「建议」与「Bug」两种类型；
 * - 发送前检查是否已绑定邮箱（开发者邮箱 + SMTP 授权码），未绑定则提示绑定；
 * - 绑定后用绑定邮箱把填入的信息按指定格式 + 日志口令，
 *   发送到该项目的开发者邮箱（索引仓库中加密邮箱解密而来）。
 *
 * 指定格式（开发者端「信息」板块据此拆分）：
 * ```
 * == TENC Store 项目反馈 ==
 * 项目: owner/repo
 * 类型: Bug / 建议
 * 反馈人: <绑定邮箱>
 * 时间: yyyy-MM-dd HH:mm:ss
 * 内容: <填入的信息>
 *
 * [日志口令:<密码本加密的发送时间>]
 * ```
 */
class FeedbackViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun init(fullName: String, devEmail: String) {
        if (_uiState.value.fullName != fullName) {
            _uiState.value = FeedbackUiState(fullName = fullName, devEmail = devEmail)
        }
    }

    fun setType(type: FeedbackType) {
        _uiState.value = _uiState.value.copy(type = type, notice = null)
    }

    fun setContent(value: String) {
        _uiState.value = _uiState.value.copy(content = value, notice = null)
    }

    /** 关闭结果弹窗。 */
    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    /** 发送反馈：绑定检查 → 指定格式 + 口令 → 发到项目开发者邮箱。 */
    fun send(context: Context) {
        val state = _uiState.value
        val settings = AppContainer.settings
        val senderEmail = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        when {
            state.devEmail.isBlank() -> {
                _uiState.value = state.copy(notice = "该项目未附带开发者邮箱，无法反馈")
            }
            state.content.isBlank() -> {
                _uiState.value = state.copy(notice = "请先填写反馈内容")
            }
            senderEmail.isBlank() || authCode.isBlank() -> {
                _uiState.value = state.copy(notice = "尚未绑定邮箱，请先到设置中绑定开发者邮箱与授权码")
            }
            !settings.developerEmailVerified.value -> {
                _uiState.value = state.copy(notice = "邮箱未验证，请先到设置中发送验证邮件")
            }
            else -> {
                _uiState.value = state.copy(busy = true, notice = "正在发送反馈…")
                viewModelScope.launch {
                    val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    val body = buildString {
                        appendLine("== TENC Store 项目反馈 ==")
                        appendLine("项目: ${state.fullName}")
                        appendLine("类型: ${state.type.label}")
                        appendLine("反馈人: $senderEmail")
                        appendLine("时间: ${timeFmt.format(Date())}")
                        appendLine("内容: ${state.content}")
                        appendLine()
                        append(LogToken.generate())
                    }
                    val err = MailClient.send(
                        from = senderEmail,
                        authCode = authCode,
                        to = state.devEmail,
                        subject = "TENC Store 项目反馈",
                        body = body,
                    )
                    if (err == null) {
                        AppContainer.log(
                            "反馈",
                            "已向 ${state.fullName} 的开发者（${state.devEmail}）发送${state.type.label}反馈",
                        )
                        _uiState.value = _uiState.value.copy(
                            busy = false,
                            notice = "反馈已发送给项目开发者",
                            content = "",
                        )
                    } else {
                        AppContainer.log("反馈", "反馈发送失败：$err")
                        _uiState.value = _uiState.value.copy(
                            busy = false,
                            notice = "发送失败：${err.take(80)}",
                        )
                    }
                }
            }
        }
    }
}
