package com.tengwear.tengstore.data

import com.tengwear.tengstore.ui.util.TimeToken

/**
 * 日志口令：测试人员发送日志邮件时，应用自动在邮件正文末尾附加一段口令——
 * 口令内容 = 用内置密码本加密的「邮件发送时间」。
 *
 * 开发者端「信息」板块收到邮件后：
 * 1. 从正文提取口令并用同一密码本解密出发送时间；
 * 2. 与邮件的实际接收时间比对，误差 ≤ [WINDOW_MINUTES] 分钟才视为有效；
 * 3. 不含口令、口令无法解密或超时的邮件一律不显示。
 */
object LogToken {

    const val TOKEN_PREFIX = "[日志口令:"
    const val TOKEN_SUFFIX = "]"

    /** 口令有效期：发送与接收时间误差上限（分钟）。 */
    const val WINDOW_MINUTES = 5L

    /** 生成附加在邮件正文末尾的口令行。 */
    fun generate(nowMillis: Long = System.currentTimeMillis()): String =
        TOKEN_PREFIX + TimeToken.encrypt(nowMillis, Secrets.LOG_CODEBOOK) + TOKEN_SUFFIX

    /** 从邮件正文中提取口令令牌（不含前后缀）；没有则返回 null。 */
    fun extract(body: String): String? {
        val start = body.indexOf(TOKEN_PREFIX)
        if (start < 0) return null
        val end = body.indexOf(TOKEN_SUFFIX, start)
        if (end < 0) return null
        val token = body.substring(start + TOKEN_PREFIX.length, end).trim()
        return token.ifBlank { null }
    }

    /**
     * 校验邮件正文：提取口令 → 解密出发送时间 → 与 [receivedMillis] 比对。
     * @return 校验通过返回解密出的发送时间戳；无口令/解密失败/超时返回 null。
     */
    fun validate(body: String, receivedMillis: Long): Long? {
        val token = extract(body) ?: return null
        val sentAt = TimeToken.decrypt(token, Secrets.LOG_CODEBOOK) ?: return null
        return if (kotlin.math.abs(receivedMillis - sentAt) <= WINDOW_MINUTES * 60 * 1000L) {
            sentAt
        } else {
            null
        }
    }
}
