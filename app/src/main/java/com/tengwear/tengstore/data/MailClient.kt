package com.tengwear.tengstore.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Part
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SubjectTerm

/**
 * 应用内收发邮件：
 * - 发（SMTP）：像邮箱软件一样，用用户自己的邮箱（SMTP 授权码）直接在应用内发送；
 * - 收（IMAP）：开发者端「信息」板块拉取收件箱，只保留通过日志口令校验的邮件。
 *
 * 常见邮箱的 SMTP/IMAP 配置按邮箱地址自动推断。
 */
object MailClient {

    data class SmtpConfig(
        val host: String,
        val port: Int,
        val ssl: Boolean,
    )

    /** 按邮箱地址推断 SMTP 服务器。 */
    fun inferSmtp(email: String): SmtpConfig {
        val domain = email.substringAfter("@", "").lowercase()
        return when {
            domain == "qq.com" || domain == "foxmail.com" -> SmtpConfig("smtp.qq.com", 465, true)
            domain == "163.com" -> SmtpConfig("smtp.163.com", 465, true)
            domain == "126.com" -> SmtpConfig("smtp.126.com", 465, true)
            domain == "sina.com" -> SmtpConfig("smtp.sina.com", 465, true)
            domain == "gmail.com" -> SmtpConfig("smtp.gmail.com", 465, true)
            domain == "outlook.com" || domain == "hotmail.com" || domain == "live.com" ->
                SmtpConfig("smtp.office365.com", 587, false)
            domain.isNotEmpty() -> SmtpConfig("smtp.$domain", 465, true)
            else -> SmtpConfig("", 465, true)
        }
    }

    /**
     * 应用内直接发送一封邮件。
     * @param from 发件邮箱（用户自己的邮箱）
     * @param authCode SMTP 授权码（不是邮箱登录密码）
     * @param to 收件邮箱（邮箱托管场景下通常也是开发者自己的邮箱）
     * @return 发送成功返回 null；失败返回错误描述。
     */
    suspend fun send(from: String, authCode: String, to: String, subject: String, body: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val config = inferSmtp(from)
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    if (config.ssl) {
                        put("mail.smtp.ssl.enable", "true")
                        put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                        put("mail.smtp.socketFactory.port", config.port.toString())
                    } else {
                        put("mail.smtp.starttls.enable", "true")
                    }
                    put("mail.smtp.connectiontimeout", "15000")
                    put("mail.smtp.timeout", "20000")
                    put("mail.smtp.host", config.host)
                    put("mail.smtp.port", config.port.toString())
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication =
                        PasswordAuthentication(from, authCode)
                })
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(from))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    this.subject = MimeUtilityEncode.subject(subject)
                    setText(body, "UTF-8")
                }
                Transport.send(message)
            }.fold(
                onSuccess = { null },
                onFailure = { e -> friendly(e, "发送") },
            )
        }

    /** 把邮件服务器错误翻译成用户能看懂、知道怎么修的提示。 */
    private fun friendly(e: Throwable, action: String): String {
        val raw = (e.message ?: "${action}失败").replace('\n', ' ')
        val lower = raw.lowercase()
        return when {
            // 535 / 530 / "Login fail" / Authentication failed：账号或授权码问题
            "535" in raw || "530" in raw || "login fail" in lower ||
                "authentication failed" in lower || "auth fail" in lower ||
                "invalid user" in lower || "user was locked" in lower -> {
                when {
                    "abnormal" in lower || "locked" in lower ->
                        "登录被邮箱服务器拒绝（账号异常）：请确认已开启 SMTP/IMAP 服务，并用最新生成的授权码。QQ 邮箱：网页版 设置→账户→开启服务→生成授权码；若刚开启需等几分钟再试"
                    "163" in raw || "126" in raw ->
                        "登录被邮箱服务器拒绝：163/126 需在网页版 设置→POP3/SMTP/IMAP 开启服务并使用授权码登录"
                    else ->
                        "登录被邮箱服务器拒绝：请确认使用的是授权码（不是邮箱登录密码），且已开启 SMTP/IMAP 服务"
                }
            }
            "unknownhost" in lower || "connect" in lower || "timeout" in lower ->
                "无法连接邮件服务器：请检查手表网络后重试"
            else -> "${action}失败：${raw.take(120)}"
        }
    }

    /** 按邮箱地址推断 IMAP 服务器。 */
    fun inferImap(email: String): SmtpConfig {
        val domain = email.substringAfter("@", "").lowercase()
        return when {
            domain == "qq.com" || domain == "foxmail.com" -> SmtpConfig("imap.qq.com", 993, true)
            domain == "163.com" -> SmtpConfig("imap.163.com", 993, true)
            domain == "126.com" -> SmtpConfig("imap.126.com", 993, true)
            domain == "sina.com" -> SmtpConfig("imap.sina.com", 993, true)
            domain == "gmail.com" -> SmtpConfig("imap.gmail.com", 993, true)
            domain == "outlook.com" || domain == "hotmail.com" || domain == "live.com" ->
                SmtpConfig("outlook.office365.com", 993, true)
            domain.isNotEmpty() -> SmtpConfig("imap.$domain", 993, true)
            else -> SmtpConfig("", 993, true)
        }
    }

    /** 校验通过的信息邮件：只含日志正文（口令行已剥离）。 */
    data class InboxMail(
        val from: String,
        val receivedAt: Long,
        val sentAt: Long,
        val body: String,
    )

    /**
     * 开发者端拉取信息（IMAP）：
     * 只搜索主题含 [subjectKeyword] 且接收时间在最近 24 小时内的邮件，
     * 逐封用 [LogToken.validate] 校验口令（解密发送时间与接收时间误差 ≤5 分钟），
     * 只返回校验通过的邮件；不含口令或其他邮件一律不返回。
     *
     * @return 校验通过的信息列表；第二个值为错误描述（null 表示成功）。
     */
    suspend fun fetchVerifiedInbox(
        email: String,
        authCode: String,
        subjectKeyword: String,
    ): Pair<List<InboxMail>, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val config = inferImap(email)
            val props = Properties().apply {
                put("mail.imap.ssl.enable", "true")
                put("mail.imap.host", config.host)
                put("mail.imap.port", config.port.toString())
                put("mail.imap.connectiontimeout", "15000")
                put("mail.imap.timeout", "25000")
            }
            val session = Session.getInstance(props)
            val store = session.getStore("imap")
            store.connect(config.host, email, authCode)
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            val since = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
            val msgs = inbox.search(
                AndTerm(
                    SubjectTerm(subjectKeyword),
                    ReceivedDateTerm(ComparisonTerm.GE, since),
                ),
            )
            val items = msgs.mapNotNull { msg ->
                val received = runCatching { msg.receivedDate?.time }.getOrNull() ?: return@mapNotNull null
                val body = textOf(msg)
                val sentAt = LogToken.validate(body, received) ?: return@mapNotNull null
                InboxMail(
                    from = runCatching {
                        (msg.from?.firstOrNull() as? InternetAddress)?.address ?: email
                    }.getOrDefault(email),
                    receivedAt = received,
                    sentAt = sentAt,
                    body = body.substringBefore(LogToken.TOKEN_PREFIX).trim(),
                )
            }.sortedByDescending { it.receivedAt }
            inbox.close(false)
            store.close()
            items
        }.fold(
            onSuccess = { it to null },
            onFailure = { e -> emptyList<InboxMail>() to friendly(e, "拉取") },
        )
    }

    /** 递归提取邮件纯文本正文。 */
    private fun textOf(part: Part): String = runCatching {
        when {
            part.isMimeType("text/plain") -> part.content.toString()
            part.isMimeType("multipart/*") -> {
                val mp = part.content as MimeMultipart
                (0 until mp.count).mapNotNull { i ->
                    runCatching { textOf(mp.getBodyPart(i)) }.getOrNull()
                }.joinToString("\n")
            }
            else -> ""
        }
    }.getOrDefault("")
}

/** RFC 2047 编码邮件主题，保证中文主题不乱码。 */
private object MimeUtilityEncode {
    fun subject(text: String): String {
        val encoded = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "=?UTF-8?B?$encoded?="
    }
}
