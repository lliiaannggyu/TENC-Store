package com.tengwear.tengstore.ui.inbox

import android.widget.Toast
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

/** 信息类型：日志 / 项目反馈 / 应用投稿 / 投稿结果。 */
enum class InboxKind(val label: String) {
    LOG("日志"),
    FEEDBACK("反馈"),
    SUBMISSION("投稿"),
    REVIEW("审核结果"),
}

data class InboxItem(
    val kind: InboxKind,
    val from: String,
    val subject: String,
    val receivedAt: Long,
    val sentAt: Long,
    val body: String,
    /** 按指定格式拆分出的字段。 */
    val project: String? = null,
    val type: String? = null,
    val sender: String? = null,
    /** 投稿：GitHub 链接（owner/repo 或完整链接）。 */
    val link: String? = null,
    /** 投稿：一句话描述功能（审核时必须可见）。 */
    val desc: String? = null,
    /** 投稿结果：通过 / 打回。 */
    val reviewResult: String? = null,
    /** 投稿结果：打回理由。 */
    val reviewReason: String? = null,
) {
    /** 投稿人的回复邮箱：投稿人字段优先，回退发件人。 */
    val replyTo: String? get() = sender?.takeIf { it.contains("@") } ?: from
}

data class InboxUiState(
    val loading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    val error: String? = null,
    /** 一次拉取中被口令校验淘汰掉的邮件数（对开发者透明可见）。 */
    val rejected: Int = 0,
    /** 正在处理的投稿链接（确认收录 / 打回回复中）。 */
    val handling: String? = null,
)

/**
 * 信息板块：接收日志反馈、项目反馈、应用投稿与投稿结果邮件。
 *
 * 接收规则：
 * - 用开发者邮箱（IMAP + 授权码）拉取最近 24 小时内、主题含「TENC Store」的邮件；
 * - 每封邮件正文须携带日志口令（内置密码本加密的发送时间），
 *   解密出的发送时间与邮件接收时间误差 ≤ 24 小时（与拉取窗口一致）才有效；
 * - 不含口令、口令无效或超时的邮件一律不显示。
 *
 * 投稿处理：
 * - 确认收录 → 自动把投稿仓库追加到测试索引仓库 repos.txt；
 * - 打回 → 填写理由后自动给投稿人发一封「TENC Store 投稿结果」邮件。
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
                        val items = verified.map { parseFields(it) }
                        AppContainer.log("信息", "信息拉取完成：有效 ${items.size} 封")
                        _uiState.value = InboxUiState(items = items)
                        // 已读时间：之后打开应用只把比这更新的邮件算作新信息
                        verified.maxOfOrNull { it.receivedAt }?.let { settings.lastInboxSeenAt = it }
                    }
                }
            }
        }
    }

    /** 确认收录投稿：把投稿仓库追加到测试索引仓库 repos.txt（带投稿人加密邮箱）。 */
    fun approveSubmission(item: InboxItem) {
        val link = item.link?.let { normalizeRepo(it) }
        if (link == null) {
            toast("投稿里没有可识别的仓库链接")
            return
        }
        val settings = AppContainer.settings
        val hub = settings.parseRepo(settings.testHubRepo.value)
        if (hub == null) {
            toast("请先在设置里配置测试应用仓库")
            return
        }
        if (settings.currentToken().isNullOrBlank()) {
            toast("请先在设置里登录 GitHub 令牌")
            return
        }
        if (item.handled) return
        markHandling(item)
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val (text, sha) = AppContainer.gitHubRepository
                        .fileWithSha(hub.first, hub.second, "repos.txt")
                    val lines = text.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                    if (lines.any { DevMailCipher.parseRepo(it)?.first == link }) {
                        return@withContext false // 已存在
                    }
                    val email = DevMailCipher.encode(item.replyTo ?: "")
                    val newLine = buildString {
                        append("https://github.com/").append(link)
                        if (item.replyTo != null) append(" ").append(email)
                    }
                    AppContainer.gitHubRepository.updateTextFile(
                        hub.first, hub.second, "repos.txt", sha,
                        (lines + newLine).joinToString("\n", postfix = "\n"),
                        "chore: 收录应用投稿 $link",
                    )
                    true
                }
            }
            clearHandling(item)
            r.fold(
                onSuccess = { added ->
                    if (added) {
                        AppContainer.log("信息", "已收录投稿 $link")
                        toast("已收录 $link")
                        removeItem(item)
                    } else {
                        toast("该应用已在测试列表中")
                        removeItem(item)
                    }
                },
                onFailure = { e ->
                    AppContainer.log("信息", "收录投稿失败：${e.message}")
                    toast("收录失败：${e.message?.take(60) ?: "未知错误"}")
                },
            )
        }
    }

    /** 打回投稿：给投稿人发一封投稿结果邮件（理由必填由调用方保证）。 */
    fun rejectSubmission(item: InboxItem, reason: String) {
        val to = item.replyTo
        if (to == null) {
            toast("投稿里没有投稿人邮箱，无法回复")
            return
        }
        if (item.handled) return
        markHandling(item)
        val settings = AppContainer.settings
        val from = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        viewModelScope.launch {
            val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val link = item.link?.let { normalizeRepo(it) } ?: item.project ?: "未知仓库"
            val body = buildString {
                appendLine("== TENC Store 投稿结果 ==")
                appendLine("项目: $link")
                appendLine("结果: 打回")
                appendLine("理由: ${reason.trim()}")
                appendLine("时间: ${timeFmt.format(Date())}")
                appendLine()
                append(LogToken.generate())
            }
            val err = MailClient.send(
                from = from,
                authCode = authCode,
                to = to,
                subject = "TENC Store 投稿结果",
                body = body,
            )
            clearHandling(item)
            if (err == null) {
                AppContainer.log("信息", "已打回投稿 $link 并邮件通知 $to")
                toast("已打回并邮件通知投稿人")
                removeItem(item)
            } else {
                AppContainer.log("信息", "打回邮件发送失败：$err")
                toast("通知邮件发送失败：${err.take(60)}")
            }
        }
    }

    private var handledLinks = mutableSetOf<String>()

    private val InboxItem.handled: Boolean
        get() = handledLinks.contains(link ?: "#${receivedAt}")

    private fun markHandling(item: InboxItem) {
        handledLinks.add(item.link ?: "#${item.receivedAt}")
        _uiState.value = _uiState.value.copy(handling = item.link)
    }

    private fun clearHandling(item: InboxItem) {
        _uiState.value = _uiState.value.copy(handling = null)
    }

    private fun removeItem(item: InboxItem) {
        handledLinks.add(item.link ?: "#${item.receivedAt}")
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.filterNot { it.receivedAt == item.receivedAt && it.from == item.from },
        )
    }

    private fun toast(text: String) {
        Toast.makeText(AppContainer.context, text, Toast.LENGTH_SHORT).show()
    }

    /** 投稿链接统一成 owner/repo。 */
    fun normalizeRepo(raw: String): String? {
        val t = raw.trim()
            .removePrefix("https://github.com/").removePrefix("http://github.com/")
            .removeSuffix("/").removeSuffix(".git").trim('/')
        return if (Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(t)) t else null
    }

    companion object {
        /**
         * 邮件主题关键字：日志反馈、项目反馈、应用投稿、投稿结果都包含它。
         */
        const val LOG_SUBJECT = "TENC Store"

        /** 识别为字段行的键（值单列展示，不混入正文）。 */
        private val FIELD_KEYS = setOf("项目", "类型", "反馈人", "链接", "投稿人", "结果", "理由")

        /** 值并入正文的键（修复「信息没有正文」：内容/描述才是正文本体）。 */
        private val CONTENT_KEYS = setOf("内容", "描述")

        /** 仅作记录、不展示的键。 */
        private val SKIP_KEYS = setOf("时间")

        /**
         * 按主题判定类型，并按指定格式拆分邮件正文：
         * `== 标题 ==` 行、`字段: 值` 行进 fields；内容/描述行的值并入正文；其余行保留为正文。
         */
        fun parseFields(mail: MailClient.InboxMail): InboxItem {
            val kind = when {
                mail.subject.contains("应用投稿") -> InboxKind.SUBMISSION
                mail.subject.contains("投稿结果") -> InboxKind.REVIEW
                mail.subject.contains("项目反馈") -> InboxKind.FEEDBACK
                else -> InboxKind.LOG
            }
            val fields = mutableMapOf<String, String>()
            val rest = StringBuilder()
            mail.body.lines().forEach { line ->
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("==")) return@forEach
                val idx = t.indexOfFirst { it == ':' || it == '：' }
                val key = if (idx > 0) t.substring(0, idx).trim() else ""
                when {
                    idx > 0 && key in FIELD_KEYS -> fields[key] = t.substring(idx + 1).trim()
                    idx > 0 && key in CONTENT_KEYS ->
                        t.substring(idx + 1).trim().takeIf { it.isNotEmpty() }?.let { rest.appendLine(it) }
                    idx > 0 && key in SKIP_KEYS -> Unit
                    else -> rest.appendLine(t)
                }
            }
            return InboxItem(
                kind = kind,
                from = mail.from,
                subject = mail.subject,
                receivedAt = mail.receivedAt,
                sentAt = mail.sentAt,
                body = rest.toString().trim(),
                project = fields["项目"],
                type = fields["类型"],
                sender = fields["反馈人"] ?: fields["投稿人"],
                link = fields["链接"],
                desc = fields["描述"],
                reviewResult = fields["结果"],
                reviewReason = fields["理由"],
            )
        }
    }
}
