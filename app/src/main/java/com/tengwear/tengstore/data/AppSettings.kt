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

    /** 使用协议：是否已在软件开头阅读并确认（滑动到协议底部后才能确认）。 */
    private val _agreementAccepted =
        MutableStateFlow(prefs.getBoolean(KEY_AGREEMENT_ACCEPTED, false))
    val agreementAccepted = _agreementAccepted.asStateFlow()

    fun acceptAgreement() {
        _agreementAccepted.value = true
        prefs.edit().putBoolean(KEY_AGREEMENT_ACCEPTED, true).apply()
    }

    /**
     * 授权记录：商店给本机其他应用签发的授权（邮箱等），仅存本地。
     * 每条：应用名 | 被授权邮箱 | 签发时间 | 授权码（内置密码本加密，可验证归属）。
     * 授权协议 v2：可选附带请求方的 nonce（防重放）与 device（设备指纹），
     * 旧记录缺字段反序列化为 null。
     */
    data class AuthGrant(
        val app: String,
        val email: String,
        val createdAt: Long,
        val code: String,
        val nonce: String? = null,
        val device: String? = null,
    )

    private val _authGrants = MutableStateFlow(readAuthGrants())
    val authGrants = _authGrants.asStateFlow()

    private fun readAuthGrants(): List<AuthGrant> =
        (readString(KEY_AUTH_GRANTS) ?: "").lineSequence()
            .mapNotNull { l ->
                val p = l.split("|")
                if (p.size < 4) return@mapNotNull null
                val rest = p.drop(3)
                // 新格式在 code 之后追加 `n:{nonce}` / `d:{device}` 字段；
                // 授权码为标准 Base64（不含冒号），以 n:/d: 前缀即可与旧格式区分。
                val hasV2 = rest.drop(1).any { it.startsWith("n:") || it.startsWith("d:") }
                if (hasV2) {
                    var nonce: String? = null
                    var device: String? = null
                    rest.drop(1).forEach { f ->
                        when {
                            f.startsWith("n:") -> nonce = f.substring(2).ifBlank { null }
                            f.startsWith("d:") -> device = f.substring(2).ifBlank { null }
                        }
                    }
                    AuthGrant(p[0], p[1], p[2].toLongOrNull() ?: 0L, p[3], nonce, device)
                } else {
                    // 旧格式：code 位于第 4 段起（可能含 |）
                    AuthGrant(p[0], p[1], p[2].toLongOrNull() ?: 0L, rest.joinToString("|"))
                }
            }
            .toList()

    private fun writeAuthGrants(grants: List<AuthGrant>) {
        val text = grants.joinToString("\n") { g ->
            buildString {
                append("${g.app}|${g.email}|${g.createdAt}|${g.code}")
                if (g.nonce != null) append("|n:${g.nonce}")
                if (g.device != null) append("|d:${g.device}")
            }
        }
        _authGrants.value = grants
        prefs.edit().putString(KEY_AUTH_GRANTS, text).apply()
    }

    /**
     * 给 [app] 签发邮箱授权，返回授权码（加密文本，供被授权应用录入校验）。
     *
     * 授权协议 v2 明文格式（通用，可选字段缺省时省略）：
     * ```
     * TENC-GRANT app={应用名} email={开发者邮箱} time={毫秒时间戳}[ n={nonce}][ d={device}]
     * ```
     * 应用名与邮箱一律用 trim 后的值；加密仍为
     * TimeToken.encryptText(明文, Secrets.LOG_CODEBOOK)，算法与密码本严格不变。
     * 新增字段对旧版被授权应用无害（它们只检查前缀与 app= 字段）。
     */
    fun addAuthGrant(
        app: String,
        email: String,
        nonce: String? = null,
        device: String? = null,
    ): AuthGrant {
        val now = System.currentTimeMillis()
        val a = app.trim()
        val e = email.trim()
        val plain = buildString {
            append("TENC-GRANT app=$a email=$e time=$now")
            nonce?.trim()?.takeIf { it.isNotBlank() }?.let { append(" n=$it") }
            device?.trim()?.takeIf { it.isNotBlank() }?.let { append(" d=$it") }
        }
        val grant = AuthGrant(
            app = a,
            email = e,
            createdAt = now,
            code = com.tengwear.tengstore.ui.util.TimeToken.encryptText(plain, Secrets.LOG_CODEBOOK),
            nonce = nonce?.trim()?.takeIf { it.isNotBlank() },
            device = device?.trim()?.takeIf { it.isNotBlank() },
        )
        writeAuthGrants(_authGrants.value + grant)
        return grant
    }

    /** 撤销一条授权。 */
    fun removeAuthGrant(grant: AuthGrant) {
        writeAuthGrants(_authGrants.value.filterNot { it == grant })
    }

    /**
     * 翻译令牌：用户自己申请的彩云小译 Token（仅存本地）。
     * 应用不内置任何令牌，翻译额度全部消耗在用户自己的账户上。
     */
    private val _translateToken = MutableStateFlow(readString(KEY_TRANSLATE_TOKEN))
    val translateToken = _translateToken.asStateFlow()

    fun setTranslateToken(value: String?) {
        _translateToken.value = write(KEY_TRANSLATE_TOKEN, value?.takeIf { it.isNotBlank() })
    }

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

    /** 已看过的测试项目版本（repo → 最新已看 tag），用于「有更新」表情。 */
    private val _seenTags = MutableStateFlow(readSeenTags())
    val seenTags = _seenTags.asStateFlow()

    /** 信息板块：上次已读时间戳（之后的邮件视为新信息）。 */
    var lastInboxSeenAt: Long
        get() = prefs.getLong(KEY_LAST_INBOX_SEEN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_INBOX_SEEN, value).apply()

    /** 审核结果：上次已读时间戳（之后的投稿结果触发全屏提醒）。 */
    var lastReviewSeenAt: Long
        get() = prefs.getLong(KEY_LAST_REVIEW_SEEN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REVIEW_SEEN, value).apply()

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

    /** 读取某项目已看过的最新版本 tag；没看过返回 null。 */
    fun seenTagOf(fullName: String): String? = _seenTags.value[fullName]

    /** 标记某项目已看到某版本（看过即不再显示「有更新」表情）。 */
    fun markSeenTag(fullName: String, tag: String) {
        val next = _seenTags.value.toMutableMap().apply { put(fullName, tag) }
        prefs.edit().putString(KEY_SEEN_TAGS, org.json.JSONObject(next.toSortedMap()).toString()).apply()
        _seenTags.value = next
    }

    private fun readSeenTags(): Map<String, String> = runCatching {
        val json = prefs.getString(KEY_SEEN_TAGS, null) ?: return emptyMap()
        val obj = org.json.JSONObject(json)
        buildMap {
            obj.keys().forEach { key -> put(key, obj.optString(key)) }
        }
    }.getOrDefault(emptyMap())

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

        private const val KEY_SEEN_TAGS = "seen_release_tags"
        private const val KEY_LAST_INBOX_SEEN = "last_inbox_seen_at"
        private const val KEY_LAST_REVIEW_SEEN = "last_review_seen_at"
        private const val KEY_AGREEMENT_ACCEPTED = "agreement_accepted"
        private const val KEY_AUTH_GRANTS = "auth_grants"
        private const val KEY_TRANSLATE_TOKEN = "translate_token"

        /** 默认测试项目索引仓库。 */
        const val DEFAULT_TEST_HUB_REPO = "lliiaannggyu/TENC-TestHub"

        /** 默认公告索引仓库：announcements.md 的内容即公告。 */
        const val DEFAULT_ANNOUNCE_REPO = "lliiaannggyu/TENC-Announcements"
    }
}
