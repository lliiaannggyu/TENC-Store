package com.tengwear.tengstore.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地设置存储（SharedPreferences）。
 *
 * 包含：GitHub 令牌、搜索关键词及开关、公告仓库、推送仓库、
 * 下载重命名模板、反馈邮箱、消息密码、口令（测试模式）。
 */
class AppSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _token = MutableStateFlow(readString(KEY_TOKEN))
    val token = _token.asStateFlow()

    private val _keywords = MutableStateFlow(readString(KEY_KEYWORDS) ?: "")
    val keywords = _keywords.asStateFlow()

    private val _keywordsEnabled = MutableStateFlow(prefs.getBoolean(KEY_KEYWORDS_ENABLED, true))
    val keywordsEnabled = _keywordsEnabled.asStateFlow()

    private val _announceRepo = MutableStateFlow(readString(KEY_ANNOUNCE_REPO) ?: DEFAULT_ANNOUNCE_REPO)
    val announceRepo = _announceRepo.asStateFlow()

    private val _pushRepo = MutableStateFlow(readString(KEY_PUSH_REPO) ?: "")
    val pushRepo = _pushRepo.asStateFlow()

    private val _marketRepo = MutableStateFlow(readString(KEY_MARKET_REPO) ?: "")
    val marketRepo = _marketRepo.asStateFlow()

    /** 测试项目索引仓库（owner/repo），其中 repos.txt 每行一个待测试项目仓库链接。 */
    private val _testHubRepo = MutableStateFlow(readString(KEY_TEST_HUB_REPO) ?: DEFAULT_TEST_HUB_REPO)
    val testHubRepo = _testHubRepo.asStateFlow()

    private val _renamePattern = MutableStateFlow(readString(KEY_RENAME_PATTERN) ?: "")
    val renamePattern = _renamePattern.asStateFlow()

    /**
     * 开发者邮箱：由用户自己在设置中填写（任何用户都可能是开发者），
     * 日志反馈 / 发布记录的邮箱托管都发往这个邮箱。
     * 填写后需要完成一次验证邮件的发送才视为已验证。
     */
    private val _developerEmail = MutableStateFlow(readString(KEY_DEVELOPER_EMAIL) ?: "")
    val developerEmail = _developerEmail.asStateFlow()

    private val _developerEmailVerified =
        MutableStateFlow(prefs.getBoolean(KEY_DEVELOPER_EMAIL_VERIFIED, false))
    val developerEmailVerified = _developerEmailVerified.asStateFlow()

    /** SMTP 授权码：应用内直接发邮件用（不是邮箱登录密码）。 */
    private val _smtpAuthCode = MutableStateFlow(readString(KEY_SMTP_AUTH_CODE) ?: "")
    val smtpAuthCode = _smtpAuthCode.asStateFlow()

    private val _messagePassword = MutableStateFlow(readString(KEY_MESSAGE_PASSWORD) ?: "")
    val messagePassword = _messagePassword.asStateFlow()

    /** 收藏的仓库（owner/repo 集合）。 */
    private val _favorites = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty())
    val favorites = _favorites.asStateFlow()

    /** 收藏的 GitHub 用户（login 集合）。 */
    private val _favoriteUsers = MutableStateFlow(prefs.getStringSet(KEY_FAVORITE_USERS, emptySet()).orEmpty())
    val favoriteUsers = _favoriteUsers.asStateFlow()

    /** 随机抽取：历史上已抽过的仓库（owner/repo），避免重复抽中。 */
    private val _drawnRepos = MutableStateFlow(prefs.getStringSet(KEY_DRAWN_REPOS, emptySet()).orEmpty())
    val drawnRepos = _drawnRepos.asStateFlow()

    fun addDrawnRepo(fullName: String) {
        val next = _drawnRepos.value + fullName
        prefs.edit().putStringSet(KEY_DRAWN_REPOS, next).apply()
        _drawnRepos.value = next
    }

    /** 浏览历史（owner/repo，最新在前，最多 20 条）。 */
    private val _history = MutableStateFlow(
        readString(KEY_HISTORY)?.split("\n")?.filter { it.isNotBlank() }.orEmpty(),
    )
    val history = _history.asStateFlow()

    /** 服务端推送：上次已读到的 Release id（-1 表示没有记录）。 */
    var lastPushReleaseId: Long
        get() = prefs.getLong(KEY_LAST_PUSH_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_PUSH_ID, value).apply()

    fun currentToken(): String? = readString(KEY_TOKEN)

    fun setToken(value: String?) { _token.value = write(KEY_TOKEN, value) }

    fun setKeywords(value: String?) { _keywords.value = write(KEY_KEYWORDS, value) }

    fun setKeywordsEnabled(value: Boolean) =
        prefs.edit().putBoolean(KEY_KEYWORDS_ENABLED, value).apply().also { _keywordsEnabled.value = value }

    fun setAnnounceRepo(value: String?) { _announceRepo.value = write(KEY_ANNOUNCE_REPO, value) }

    fun setPushRepo(value: String?) { _pushRepo.value = write(KEY_PUSH_REPO, value) }

    fun setMarketRepo(value: String?) { _marketRepo.value = write(KEY_MARKET_REPO, value) }

    fun setTestHubRepo(value: String?) { _testHubRepo.value = write(KEY_TEST_HUB_REPO, value) }

    fun setRenamePattern(value: String?) { _renamePattern.value = write(KEY_RENAME_PATTERN, value) }

    fun setSmtpAuthCode(value: String?) { _smtpAuthCode.value = write(KEY_SMTP_AUTH_CODE, value) }

    /** 保存开发者邮箱；邮箱变动后验证状态自动失效，需重新验证。 */
    fun setDeveloperEmail(value: String?) {
        _developerEmail.value = write(KEY_DEVELOPER_EMAIL, value)
        setDeveloperEmailVerified(false)
    }

    /** 标记开发者邮箱已完成验证（用户发出验证邮件后确认）。 */
    fun setDeveloperEmailVerified(value: Boolean) {
        prefs.edit().putBoolean(KEY_DEVELOPER_EMAIL_VERIFIED, value).apply()
        _developerEmailVerified.value = value
    }

    fun setMessagePassword(value: String?) { _messagePassword.value = write(KEY_MESSAGE_PASSWORD, value) }

    /** 收藏 / 取消收藏一个仓库。 */
    fun toggleFavorite(fullName: String) {
        val next = _favorites.value.toMutableSet()
        if (!next.add(fullName)) next.remove(fullName)
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
        _favorites.value = next
    }

    /** 收藏 / 取消收藏一个 GitHub 用户。 */
    fun toggleFavoriteUser(user: String) {
        val next = _favoriteUsers.value.toMutableSet()
        if (!next.add(user)) next.remove(user)
        prefs.edit().putStringSet(KEY_FAVORITE_USERS, next).apply()
        _favoriteUsers.value = next
    }

    /** 记录一次仓库浏览（去重、最新在前、上限 20 条）。 */
    fun addHistory(fullName: String) {
        if (fullName.isBlank()) return
        val next = _history.value.toMutableList().apply {
            remove(fullName)
            add(0, fullName)
        }.take(HISTORY_LIMIT)
        prefs.edit().putString(KEY_HISTORY, next.joinToString("\n")).apply()
        _history.value = next
    }

    /** 清空浏览历史。 */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _history.value = emptyList()
    }

    /** 解析 owner/repo 形式的仓库地址；非法时返回 null。 */
    fun parseRepo(raw: String?): Pair<String, String>? {
        val parts = raw?.trim()?.split("/")?.filter { it.isNotBlank() }
        return if (parts?.size == 2) parts[0] to parts[1] else null
    }

    private fun readString(key: String): String? = prefs.getString(key, null)

    private fun write(key: String, value: String?): String {
        val trimmed = value?.trim()?.takeIf { it.isNotBlank() }
        prefs.edit().putString(key, trimmed).apply()
        return trimmed.orEmpty()
    }

    companion object {
        private const val PREFS_NAME = "wear_gh_store"
        private const val KEY_TOKEN = "gh_token"
        private const val KEY_KEYWORDS = "search_keywords"
        private const val KEY_KEYWORDS_ENABLED = "keywords_enabled"
        private const val KEY_ANNOUNCE_REPO = "announce_repo"
        private const val KEY_PUSH_REPO = "push_repo"
        private const val KEY_MARKET_REPO = "market_repo"
        private const val KEY_TEST_HUB_REPO = "test_hub_repo"
        private const val KEY_RENAME_PATTERN = "rename_pattern"
        private const val KEY_DEVELOPER_EMAIL = "developer_email"
        private const val KEY_DEVELOPER_EMAIL_VERIFIED = "developer_email_verified"
        private const val KEY_SMTP_AUTH_CODE = "smtp_auth_code"
        private const val KEY_MESSAGE_PASSWORD = "message_password"
        private const val KEY_LAST_PUSH_ID = "last_push_release_id"
        private const val KEY_FAVORITES = "favorite_repos"
        private const val KEY_FAVORITE_USERS = "favorite_users"
        private const val KEY_DRAWN_REPOS = "drawn_repos"
        private const val KEY_HISTORY = "browse_history"

        /** 浏览历史上限。 */
        private const val HISTORY_LIMIT = 20

        /** 发布说明中携带测试口令的固定格式：[测试口令:XXXX]。 */
        const val TEST_CODE_MARKER_PREFIX = "[测试口令:"
        const val TEST_CODE_MARKER_SUFFIX = "]"

        /** 默认测试项目索引仓库。 */
        const val DEFAULT_TEST_HUB_REPO = "lliiaannggyu/TENC-TestHub"

        /** 默认公告索引仓库：announcements.md 的内容即公告。 */
        const val DEFAULT_ANNOUNCE_REPO = "lliiaannggyu/TENC-Announcements"
    }
}
