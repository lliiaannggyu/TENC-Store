package com.tengwear.tengstore.data

/**
 * 测试口令与发布说明之间的编解码约定。
 *
 * 开发者上传测试应用时选择一个口令，发布说明里会写入固定格式
 * `[测试口令:XXXX]`；测试者拿到开发者分享的口令后，在「加入测试」
 * 中输入，应用拉取市场仓库并解析每个测试 Release 的口令标记，
 * 匹配成功即解锁该测试应用的下载。
 */
object TestCode {

    private val MARKER_REGEX =
        Regex("""\[测试口令[:：]\s*([^\]\s]+)\s*\]""")

    /** 从发布说明中解析出口令；没有标记时返回 null。 */
    fun parse(body: String?): String? =
        body?.let { MARKER_REGEX.find(it)?.groupValues?.get(1) }

    /** 生成写入发布说明的标记文本。 */
    fun marker(code: String): String =
        "${AppSettings.TEST_CODE_MARKER_PREFIX}${code.trim()}${AppSettings.TEST_CODE_MARKER_SUFFIX}"

    /** 判断某个 Release（按 tag/名称/预发布属性）是否属于测试发布。 */
    fun isTestRelease(tagName: String, prerelease: Boolean): Boolean =
        prerelease || Regex("beta|alpha|test|内测|测试", RegexOption.IGNORE_CASE)
            .containsMatchIn(tagName)
}
