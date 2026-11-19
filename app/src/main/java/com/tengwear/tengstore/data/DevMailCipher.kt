package com.tengwear.tengstore.data

import com.tengwear.tengstore.ui.util.TimeToken

/**
 * 开发者加密邮箱：测试项目索引仓库 repos.txt 中每个项目可附带一个
 * `[dev:<密文>]` 字段——密文 = 用内置密码本加密的项目开发者邮箱。
 *
 * 应用读取索引时解密得到开发者邮箱；测试者在应用内对项目反馈
 * （建议/Bug）时，反馈邮件发往这个邮箱。
 */
/**
 * repos.txt 中一个测试应用的完整条目。
 * @param fullName 仓库 owner/repo
 * @param name 自定义显示名（可含中文，`[name:...]` 字段，缺失为 null → 回退用仓库名）
 * @param devEmail 解密后的开发者邮箱（`[dev:...]` 字段，缺失为 null）
 */
data class RepoEntry(val fullName: String, val name: String?, val devEmail: String?)

object DevMailCipher {

    const val MARK_PREFIX = "[dev:"
    const val MARK_SUFFIX = "]"
    const val NAME_PREFIX = "[name:"
    const val NAME_SUFFIX = "]"

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

    /** 从行内 `[name:...]` 字段读出自定义显示名；缺失或空返回 null。 */
    fun decodeName(line: String): String? {
        val start = line.indexOf(NAME_PREFIX)
        if (start < 0) return null
        val end = line.indexOf(NAME_SUFFIX, start)
        if (end < 0) return null
        return line.substring(start + NAME_PREFIX.length, end).trim().takeIf { it.isNotEmpty() }
    }

    /** 清洗自定义名：去掉中括号、换行与首尾空白，空则返回 null。 */
    fun sanitizeName(raw: String): String? =
        raw.replace("[", "").replace("]", "").replace("\n", " ").trim().takeIf { it.isNotEmpty() }

    /**
     * 解析 repos.txt 的一行：仓库链接 + 可选的自定义名与加密邮箱字段。
     * @return 完整条目；行不合法返回 null。
     */
    fun parseEntry(line: String): RepoEntry? {
        val devEmail = decodeFromLine(line)
        val name = decodeName(line)
        var repoPart = line
        // 依次剥离 [dev:...] 与 [name:...] 字段，剩下的才是仓库链接
        listOf(MARK_PREFIX to MARK_SUFFIX, NAME_PREFIX to NAME_SUFFIX).forEach { (p, s) ->
            val st = repoPart.indexOf(p)
            if (st >= 0) {
                val en = repoPart.indexOf(s, st)
                if (en > st) repoPart = repoPart.removeRange(st, en + s.length)
            }
        }
        val trimmed = repoPart.trim()
        val fullName = Regex(
            "github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$",
        ).find(trimmed)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
            ?: trimmed.takeIf { Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(it) }
            ?: return null
        return RepoEntry(fullName, name, devEmail)
    }

    /**
     * 解析 repos.txt 的一行（兼容旧接口）。
     * @return (仓库 fullName, 开发者邮箱或 null)；行不合法返回 null。
     */
    fun parseRepo(line: String): Pair<String, String?>? =
        parseEntry(line)?.let { it.fullName to it.devEmail }
}
