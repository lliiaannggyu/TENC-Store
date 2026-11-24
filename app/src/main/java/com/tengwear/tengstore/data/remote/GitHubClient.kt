package com.tengwear.tengstore.data.remote

import android.content.Context
import com.tengwear.tengstore.data.AppSettings
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** GitHub API 限额/二次限流触发时抛出，携带可在手表上直接看懂的提示。 */
class GitHubRateLimitException(message: String, val resetAtSeconds: Long = 0) : IOException(message)

/**
 * 带以下能力：
 * - 用户令牌优先（设置里填了就用，额度 5000/小时；没填则匿名 60/小时）；
 * - HTTP 缓存 + ETag 条件请求（304 不计入 GitHub 限额）；
 * - 403/429 限额耗尽、二次限流、401 令牌失效的友好中文提示；
 * - 记录响应头里的剩余额度，设置页可展示。
 */
object GitHubClient {
    lateinit var api: GitHubApiService
        private set

    private val _remaining = MutableStateFlow<Int?>(null)
    /** 最近一次 GitHub 响应报出的剩余请求额度（每小时窗口）。 */
    val remaining: StateFlow<Int?> = _remaining.asStateFlow()

    private var initialized = false

    fun init(settings: AppSettings, context: Context) {
        if (initialized) return
        initialized = true
        val logging = HttpLoggingInterceptor().apply {
            // BASIC 只打 URL 与状态码，不打印令牌/请求体
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val headerInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            if (requestBuilder.build().header("Accept") == null) {
                requestBuilder.header("Accept", "application/vnd.github+json")
            }
            requestBuilder.header("X-GitHub-Api-Version", "2022-11-28")
            val token = settings.currentToken()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }
            // GET 强制条件请求：服务端 304 不扣限额，配合 OkHttp 缓存用本地数据
            if (chain.request().method == "GET") {
                requestBuilder.header("Cache-Control", "no-cache")
            }
            chain.proceed(requestBuilder.build())
        }

        val rateLimitInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            parseRemaining(response)
            when (response.code) {
                403, 429 -> {
                    if (isRateLimit(response)) {
                        val reset = response.header("x-ratelimit-reset")?.toLongOrNull() ?: 0L
                        val wait = response.header("retry-after")?.toLongOrNull()
                        val msg = when {
                            wait != null -> "GitHub 请求太频繁，请 $wait 秒后再试。可在设置里填自己的令牌提升额度。"
                            reset > 0 -> "GitHub 免费额度用完了，约在 ${TimeFormatter.time(reset)} 恢复。设置里填自己的令牌可提升到 5000 次/小时。"
                            else -> "GitHub 请求被限流，请稍后再试。设置里填自己的令牌可大幅提升额度。"
                        }
                        throw GitHubRateLimitException(msg, reset)
                    }
                }
                401 -> {
                    val hasToken = !settings.currentToken().isNullOrBlank()
                    if (hasToken) {
                        throw GitHubRateLimitException("GitHub 令牌无效或已过期，请在设置里更新令牌。")
                    }
                }
            }
            response
        }

        val cacheDir = File(context.cacheDir, "github_api_cache")
        val cache = Cache(cacheDir, 8L * 1024 * 1024)
        val okHttpClient = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(logging)
            .addInterceptor(headerInterceptor)
            .addInterceptor(rateLimitInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(GitHubApiService::class.java)
    }

    private fun parseRemaining(response: Response) {
        val remaining = response.header("x-ratelimit-remaining")?.toIntOrNull() ?: return
        _remaining.value = remaining
    }

    private fun isRateLimit(response: Response): Boolean {
        val remaining = response.header("x-ratelimit-remaining")?.toIntOrNull()
        if (remaining == 0) return true
        if (response.header("retry-after") != null) return true
        return try {
            val body = response.peekBody(4096).string()
            body.contains("rate limit", ignoreCase = true) ||
                body.contains("secondary rate", ignoreCase = true) ||
                body.contains("abuse", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private object TimeFormatter {
        fun time(epochSeconds: Long): String =
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(epochSeconds * 1000))
    }
}
