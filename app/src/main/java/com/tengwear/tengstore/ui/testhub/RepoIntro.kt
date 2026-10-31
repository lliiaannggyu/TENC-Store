package com.tengwear.tengstore.ui.testhub

/**
 * 项目介绍：从被测仓库的 README 中解析出的纯文本段落与图片。
 * 图片相对路径已转换为 raw.githubusercontent.com 的绝对地址（HEAD = 默认分支）。
 */
data class RepoIntro(
    val paragraphs: List<String> = emptyList(),
    val images: List<String> = emptyList(),
)

/** README Markdown 解析：提取正文段落与图片链接，剔除代码块 / 徽章 / HTML 标签。 */
object RepoIntroParser {

    // 徽章图片常见的关键词，过滤掉
    private val BADGE_HINTS = listOf(
        "badge", "shields.io", "travis-ci", "circleci", "codecov", "release/",
        "actions/workflows", "license.svg", "build.svg", "api-test",
    )

    private val IMAGE_MD = Regex("""!\[[^\]]*\]\(([^)\s]+)[^)]*\)""")
    private val IMG_HTML = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun parse(owner: String, repo: String, markdown: String): RepoIntro {
        val paragraphs = mutableListOf<String>()
        val images = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) paragraphs.add(text)
            buffer.clear()
        }

        var inCodeFence = false
        markdown.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (inCodeFence) return@forEach

            // 图片（Markdown 与 HTML 两种写法）
            IMAGE_MD.findAll(line).forEach { m ->
                val url = resolve(owner, repo, m.groupValues[1])
                if (isRealImage(url) && url !in images) images.add(url)
            }
            IMG_HTML.findAll(line).forEach { m ->
                val url = resolve(owner, repo, m.groupValues[1])
                if (isRealImage(url) && url !in images) images.add(url)
            }
            val textOnly = IMAGE_MD.replace(line, "")
                .replace(IMG_HTML, "")
                .replace(HTML_TAG, "")
                .trim()
            if (textOnly.isEmpty()) {
                flush()
                return@forEach
            }

            // 轻量去 Markdown：标题符 / 列表符 / 强调
            val clean = textOnly
                .removePrefix("#").trim()
                .removePrefix("-").removePrefix("*").removePrefix("+").trim()
                .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
                .replace(Regex("""__([^_]+)__"""), "$1")
                .replace(Regex("""`([^`]+)`"""), "$1")
                .trim()
            if (clean.isEmpty()) {
                flush()
            } else {
                if (textOnly.startsWith("#") && buffer.isNotEmpty()) flush()
                buffer.append(clean).append(' ')
            }
        }
        flush()

        return RepoIntro(
            paragraphs = paragraphs.take(MAX_PARAGRAPHS),
            images = images.take(MAX_IMAGES),
        )
    }

    /** 相对路径 → raw.githubusercontent.com/owner/repo/HEAD/path（HEAD 指向默认分支）。 */
    private fun resolve(owner: String, repo: String, src: String): String = when {
        src.startsWith("http://") || src.startsWith("https://") -> src
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "https://raw.githubusercontent.com/$owner/$repo/HEAD$src"
        else -> "https://raw.githubusercontent.com/$owner/$repo/HEAD/$src"
    }

    /** 过滤徽章 / icon 等小图与动态统计图，只保留真正的介绍配图。 */
    private fun isRealImage(url: String): Boolean {
        val lower = url.lowercase()
        if (BADGE_HINTS.any { it in lower }) return false
        // 徽章常来自 img.shields.io / travis 等域名之外，再按文件名短横线数量过滤过小的 svg
        if (lower.endsWith(".svg") && !lower.contains("screenshot") && !lower.contains("preview")) {
            return false
        }
        return true
    }

    private val HTML_TAG = Regex("""<[^>]+>""")

    private const val MAX_PARAGRAPHS = 12
    private const val MAX_IMAGES = 6
}
