package com.tengwear.tengstore.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.MailClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InboxItem(
    val from: String,
    val receivedAt: Long,
    val sentAt: Long,
    val body: String,
    /** 按指定格式拆分出的字段（项目反馈邮件才有）。 */
    val project: String? = null,
    val type: String? = null,
    val sender: String? = null,
)

data class InboxUiState(
    val loading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    val error: String? = null,
    /** 一次拉取中被口令校验淘汰掉的邮件数（对开发者透明可见）。 */
    val rejected: Int = 0,
)

/**
 * 信息板块：接收测试人员发来的日志邮件。
 *
 * 接收规则：
 * - 用开发者邮箱（IMAP + 授权码）拉取最近 24 小时内、主题为「TENC Store 日志反馈」的邮件；
 * - 每封邮件正文须携带日志口令（内置密码本加密的发送时间），
 *   解密出的发送时间与邮件接收时间误差 ≤ 5 分钟才有效；
 * - 不含口令、口令无效或超时的邮件一律不显示。
 */
class InboxViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    fun refresh() {
        val settings = AppContainer.settings
        val email = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        when {
            email.isBlank() -> {
                _uiState.value = InboxUiState(error = "请先在设置中填写开发者邮箱")
            }
            authCode.isBlank() -> {
                _uiState.value = InboxUiState(error = "请先在设置中填写 SMTP 授权码")
            }
            else -> {
                _uiState.value = InboxUiState(loading = true)
                viewModelScope.launch {
                    val (verified, err) = MailClient.fetchVerifiedInbox(
                        email = email,
                        authCode = authCode,
                        subjectKeyword = LOG_SUBJECT,
                    )
                    if (err != null) {
                        AppContainer.log("信息", "拉取信息失败：$err")
                        _uiState.value = InboxUiState(error = err)
                    } else {
                        AppContainer.log("信息", "信息拉取完成：有效 ${verified.size} 封")
                        _uiState.value = InboxUiState(
                            items = verified.map { parseFields(it) },
                        )
                    }
                }
            }
        }
    }

    companion object {
        /**
         * 邮件主题关键字：日志反馈（TENC Store 日志反馈）与
         * 项目反馈（TENC Store 项目反馈）都包含它。
         */
        const val LOG_SUBJECT = "TENC Store"

        /**
         * 按指定格式拆分邮件正文：
         * `== 标题 ==` 行、`字段: 值` 行（项目/类型/反馈人/时间/内容），
         * 其余行保留为正文。
         */
        fun parseFields(mail: MailClient.InboxMail): InboxItem {
            var project: String? = null
            var type: String? = null
            var sender: String? = null
            val rest = StringBuilder()
            mail.body.lines().forEach { line ->
                val t = line.trim()
                when {
                    t.startsWith("项目:") || t.startsWith("项目：") ->
                        project = t.substringAfter(':').trim().ifBlank { null }
                    t.startsWith("类型:") || t.startsWith("类型：") ->
                        type = t.substringAfter(':').trim().ifBlank { null }
                    t.startsWith("反馈人:") || t.startsWith("反馈人：") ->
                        sender = t.substringAfter(':').trim().ifBlank { null }
                    t.startsWith("==") || t.startsWith("时间:") || t.startsWith("时间：") ||
                        t.startsWith("内容:") || t.startsWith("内容：") -> Unit // 结构行，不重复展示
                    else -> if (t.isNotEmpty()) rest.appendLine(t)
                }
            }
            return InboxItem(
                from = mail.from,
                receivedAt = mail.receivedAt,
                sentAt = mail.sentAt,
                body = rest.toString().trim(),
                project = project,
                type = type,
                sender = sender,
            )
        }
    }
}
