package com.tengwear.tengstore.data

import android.content.Context
import com.tengwear.tengstore.data.remote.GitHubClient
import com.tengwear.tengstore.data.remote.GitHubApiService
import com.tengwear.tengstore.data.repository.GitHubRepository
import com.tengwear.tengstore.domain.model.Release
import java.util.LinkedHashMap

/**
 * Lightweight application-wide dependency container (no DI framework needed).
 */
object AppContainer {
    lateinit var settings: AppSettings
        private set
    lateinit var gitHubRepository: GitHubRepository
        private set
    lateinit var logStore: LogStore
        private set

    /** 应用级 Context，供无 Compose 上下文的工具函数（Toast/Intent）使用。 */
    lateinit var context: Context
        private set

    /** 应用级协程作用域（设置保存验证等后台任务用）。 */
    val appScope: kotlinx.coroutines.CoroutineScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

    fun init(context: Context) {
        this.context = context.applicationContext
        settings = AppSettings(context.applicationContext)
        logStore = LogStore(context.applicationContext)
        com.tengwear.tengstore.ui.theme.ThemePrefs.init(context.applicationContext)
        GitHubClient.init(settings)
        gitHubRepository = GitHubRepository(GitHubClient.api)
    }

    /** 记录一条应用日志（供各 ViewModel 调用）。 */
    fun log(tag: String, message: String) {
        if (::logStore.isInitialized) logStore.log(tag, message)
    }

    /**
     * Short-lived cache of releases selected on the releases screen so the
     * assets screen can read the full [Release] (including its assets) after
     * navigation without re-fetching.
     */
    val releaseCache: LinkedHashMap<Long, Release> = LinkedHashMap<Long, Release>(32)
}
