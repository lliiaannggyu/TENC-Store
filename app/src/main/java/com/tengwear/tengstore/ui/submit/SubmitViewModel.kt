package com.tengwear.tengstore.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.DevMailCipher
import com.tengwear.tengstore.data.LogToken
import com.tengwear.tengstore.data.MailClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SubmitUiState(
    /** GitHub 链接（owner/repo 或完整网址）。 */
    val link: String = "",
    /** 一句话描述功能。 */
    val desc: String = "",
    /** 自动填写的投稿邮箱（= 已绑定的开发者邮箱）。 */
    val email: String = "",
    /** 从测试索引仓库 submit.txt 解密出的投稿收件邮箱（null = 未解析 / 未开放）。 */
    val targetEmail: String? = null,
    val busy: Boolean = false,
    val notice: String? = null,
    val sent: Boolean = false,
)

/**
 * 应用投稿：
 * - 表单只有「GitHub 链接」与「一句话描述功能」两项，投稿邮箱自动使用已绑定的开发者邮箱；
 * - 投稿收件邮箱来自测试索引仓库的 submit.txt（`[dev:加密邮箱]`，内置密码本解密），
 *   收件邮箱不写死在包内、也不明文暴露；
 * - 发送主题为「TENC Store 应用投稿」的邮件（末尾自动附日志口令），
 *   开发者在应用「信息」板块收到后可确认收录或打回。
 */
class SubmitViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitUiState())
    val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

    /** 进入页面时初始化：自动填写投稿邮箱，并解析投稿收件邮箱。 */
    fun init() {
        val email = AppContainer.settings.developerEmail.value
        if (_uiState.value.email != email) {
            _uiState.value = _uiState.value.copy(email = email, notice = null)
        }
        resolveTarget()
    }

    fun setLink(value: String) {
        _uiState.value = _uiState.value.copy(link = value, notice = null)
    }

    fun setDesc(value: String) {
        _uiState.value = _uiState.value.copy(desc = value, notice = null)
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    /** 从测试索引仓库读取 submit.txt 并解密投稿收件邮箱。 */
    private fun resolveTarget() {
        viewModelScope.launch {
            val target = runCatching {
                withContext(Dispatchers.IO) {
                    val settings = AppContainer.settings
                    val hub = settings.parseRepo(settings.testHubRepo.value) ?: return@withContext null
                    val text = AppContainer.gitHubRepository.rawFile(hub.first, hub.second, "submit.txt")
                    DevMailCipher.decodeFromLine(text.trim())
                }
            }.getOrNull()
            _uiState.value = _uiState.value.copy(targetEmail = target)
        }
    }

    /** 校验并发送投稿邮件。 */
    fun send() {
        val state = _uiState.value
        val settings = AppContainer.settings
        val email = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        val link = state.link.trim()
            .removePrefix("https://github.com/").removePrefix("http://github.com/")
            .removeSuffix("/").removeSuffix(".git").trim('/')
        when {
            state.targetEmail == null -> {
                _uiState.value = state.copy(notice = "未能读取投稿收件邮箱，请检查网络后重试")
            }
            email.isBlank() || authCode.isBlank() -> {
                _uiState.value = state.copy(notice = "尚未绑定邮箱，请先到设置中绑定开发者邮箱与授权码")
            }
            !settings.developerEmailVerified.value -> {
                _uiState.value = state.copy(notice = "邮箱未验证，请先到设置中发送验证邮件")
            }
            !Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(link) -> {
                _uiState.value = state.copy(notice = "请填写正确的 GitHub 链接（owner/repo 或仓库网址）")
            }
            state.desc.isBlank() -> {
                _uiState.value = state.copy(notice = "请先用一句话描述应用功能")
            }
            else -> {
                _uiState.value = state.copy(busy = true, notice = "正在发送投稿…")
                viewModelScope.launch {
                    val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    val body = buildString {
                        appendLine("== TENC Store 应用投稿 ==")
                        appendLine("链接: $link")
                        appendLine("描述: ${state.desc.trim()}")
                        appendLine("投稿人: $email")
                        appendLine("时间: ${timeFmt.format(Date())}")
                        appendLine()
                        append(LogToken.generate())
                    }
                    val err = MailClient.send(
                        from = email,
                        authCode = authCode,
                        to = state.targetEmail!!,
                        subject = "TENC Store 应用投稿",
                        body = body,
                    )
                    if (err == null) {
                        AppContainer.log("投稿", "已投稿 $link（收件 ${state.targetEmail}）")
                        _uiState.value = SubmitUiState(
                            email = email,
                            targetEmail = state.targetEmail,
                            sent = true,
                            notice = "投稿已发送，等待审核；结果会以邮件通知",
                        )
                    } else {
                        AppContainer.log("投稿", "投稿发送失败：$err")
                        _uiState.value = state.copy(
                            busy = false,
                            notice = "发送失败：${err.take(80)}",
                        )
                    }
                }
            }
        }
    }
}
