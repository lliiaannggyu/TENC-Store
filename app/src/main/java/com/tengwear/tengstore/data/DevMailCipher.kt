package com.tengwear.tengstore.data

import com.tengwear.tengstore.ui.util.TimeToken

/**
 * 开发者加密邮箱：测试项目索引仓库 repos.txt 中每个项目可附带一个
 * `[dev:<密文>]` 字段——密文 = 用内置密码本加密的项目开发者邮箱。
 *
 * 应用读取索引时解密得到开发者邮箱；测试者在应用内对项目反馈
 * （建议/Bug）时，反馈邮件发往这个邮箱。
 */
object DevMailCipher {

    const val MARK_PREFIX = "[dev:"
    const val MARK_SUFFIX = "]"

    /** 生成 repos.txt 行内的加密邮箱字段。 */
    fun encode(email: String): String =
        MARK_PREFIX + TimeToken.encryptText(email.trim(), Secrets.LOG_CODEBOOK) + MARK_SUFFIX

    /** 从行内 `[dev:...]` 字段解密出开发者邮箱；缺失或损坏返回 null。 */
    fun decodeFromLine(line: String): String? {
        val start = line.indexOf(MARK_PREFIX)
        if (start < 0) return null
        val end = line.indexOf(MARK_SUFFIX, start)
        if (end < 0) return null
        val token = line.substring(start + MARK_PREFIX.length, end).trim()
        return TimeToken.decryptText(token, Secrets.LOG_CODEBOOK)
            ?.takeIf { it.contains("@") }
    }

    /**
     * 解析 repos.txt 的一行：仓库链接 + 可选的加密邮箱字段。
     * @return 仓库 fullName（owner/repo）；行不合法返回 null。
     */
    fun parseRepo(line: String): Pair<String, String?>? {
        // 先剥离加密邮箱字段，再按仓库链接解析
        val devEmail = decodeFromLine(line)
        var repoPart = line
        val start = repoPart.indexOf(MARK_PREFIX)
        if (start >= 0) {
            val end = repoPart.indexOf(MARK_SUFFIX, start)
            if (end > start) {
                repoPart = repoPart.removeRange(start, end + MARK_SUFFIX.length)
            }
        }
        val trimmed = repoPart.trim()
        val fullName = Regex(
            "github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$",
        ).find(trimmed)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
            ?: trimmed.takeIf { Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(it) }
            ?: return null
        return fullName to devEmail
    }
}
