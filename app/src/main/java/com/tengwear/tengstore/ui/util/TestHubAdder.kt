package com.tengwear.tengstore.ui.util

import com.tengwear.tengstore.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 快捷收录：把当前浏览的项目一键加入测试应用列表。
 *
 * 流程（全程静默校验，界面不显示令牌内容与任何仓库归属信息）：
 *  1. 读取用户已登录的 GitHub 令牌，未登录则提示去设置登录；
 *  2. 用该令牌校验对测试应用索引仓库是否有推送权限（无权限不展示入口）；
 *  3. 有权限则读取 repos.txt，查重后追加一行项目链接并提交。
 */
object TestHubAdder {

    sealed class Result {
        /** 收录成功。 */
        data object Success : Result()

        /** 该项目已在测试应用列表中。 */
        data object Already : Result()

        /** 当前令牌无权限管理测试应用仓库。 */
        data object NoAccess : Result()

        /** 未登录 GitHub 令牌。 */
        data object NoToken : Result()

        /** 其他失败（网络 / 配置缺失等）。 */
        data class Error(val message: String) : Result()
    }

    /** 当前令牌对测试应用仓库是否有推送权限；null = 无法验证。 */
    suspend fun hasAccess(): Boolean? = withContext(Dispatchers.IO) {
        val settings = AppContainer.settings
        if (settings.currentToken().isNullOrBlank()) return@withContext null
        val hub = settings.parseRepo(settings.testHubRepo.value)
            ?: return@withContext null
        AppContainer.gitHubRepository.checkPushAccess(hub.first, hub.second)
    }

    /** 把项目（owner/repo）追加进测试应用列表。 */
    suspend fun add(fullName: String): Result = withContext(Dispatchers.IO) {
        val settings = AppContainer.settings
        if (settings.currentToken().isNullOrBlank()) return@withContext Result.NoToken
        val hub = settings.parseRepo(settings.testHubRepo.value)
            ?: return@withContext Result.Error("未配置测试应用仓库，请先在设置中填写")
        when (AppContainer.gitHubRepository.checkPushAccess(hub.first, hub.second)) {
            false -> return@withContext Result.NoAccess
            null -> return@withContext Result.Error("权限校验失败，请检查网络后重试")
            else -> Unit // true：继续收录流程
        }
        val gh = AppContainer.gitHubRepository
        runCatching {
            val (text, sha) = gh.fileWithSha(hub.first, hub.second, "repos.txt")
            val lines = text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            if (lines.any { DevMailCipherLite.parseRepo(it) == fullName }) {
                return@withContext Result.Already
            }
            val newLine = "https://github.com/$fullName"
            gh.updateTextFile(
                hub.first, hub.second, "repos.txt", sha,
                (lines + newLine).joinToString("\n", postfix = "\n"),
                "chore: 快捷收录 $fullName",
            )
            Result.Success
        }.getOrElse { Result.Error(it.message?.take(60) ?: "未知错误") }
    }
}

/** repos.txt 行解析（仅取仓库 owner/repo 部分，忽略可选的加密邮箱字段）。 */
private object DevMailCipherLite {
    fun parseRepo(line: String): String? {
        val t = line.trim()
            .removePrefix("https://github.com/").removePrefix("http://github.com/")
            .substringBefore(' ')
            .removeSuffix("/").removeSuffix(".git").trim('/')
        return if (Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(t)) t else null
    }
}
