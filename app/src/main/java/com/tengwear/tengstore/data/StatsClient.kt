package com.tengwear.tengstore.data

import android.content.Context
import com.tengwear.tengstore.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 统计数据快照（来自 Worker 的 /stats 接口）。 */
data class StoreStats(
    val users: Long,       // 累积用户（去重设备数）
    val online: Long,      // 当前在线（5 分钟内活跃）
    val todayOpens: Long,  // 今日打开次数
    val totalOpens: Long,  // 累计打开次数
)

/**
 * 商店使用统计（无自建服务器方案）：
 * 后端为 Cloudflare Workers + KV（见项目 stats-worker/ 目录），
 * 应用启动时上报一次 open（计入每日/累计打开次数与累积用户），
 * 前台期间每 60 秒心跳一次（计入在线人数）。
 *
 * 隐私：设备 ID 为随机 UUID（不采集 IMEI/安卓 ID 等设备标识符），
 * 仅上报 ID 与事件类型，不包含任何个人数据。
 * BuildConfig.STATS_URL 为空时统计完全不工作（不产生任何网络请求）。
 */
object StatsClient {

    private const val PREF = "tenc_store_stats"
    private const val PREF_DEVICE_ID = "device_id"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val enabled: Boolean get() = BuildConfig.STATS_URL.isNotBlank()

    /** 匿名设备 ID：首次使用时生成随机 UUID，之后固定不变。 */
    @Synchronized
    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.getString(PREF_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(PREF_DEVICE_ID, id).apply()
        return id
    }

    /** 应用进入前台：上报一次"打开"事件（每次冷启动/回到前台计 1 次打开）。 */
    fun onForeground(context: Context) {
        if (!enabled) return
        val id = deviceId(context.applicationContext)
        scope.launch { ping("open", id) }
        // 前台心跳：每 60 秒一次，用于在线人数
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                ping("beat", id)
                delay(60_000)
            }
        }
    }

    /** 应用退到后台：停止心跳。 */
    fun onBackground() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** 拉取统计数据（供设置页展示）。返回 null 表示未配置或请求失败。 */
    suspend fun fetchStats(): StoreStats? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val req = okhttp3.Request.Builder()
                    .url("${BuildConfig.STATS_URL.trimEnd('/')}/stats")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val o = JSONObject(body)
                    StoreStats(
                        users = o.optLong("users", 0),
                        online = o.optLong("online", 0),
                        todayOpens = o.optLong("todayOpens", 0),
                        totalOpens = o.optLong("totalOpens", 0),
                    )
                }
            }.getOrNull()
        }
    }

    private suspend fun ping(event: String, id: String) = withContext(Dispatchers.IO) {
        runCatching {
            val req = okhttp3.Request.Builder()
                .url("${BuildConfig.STATS_URL.trimEnd('/')}/ping?id=$id&ev=$event")
                .build()
            client.newCall(req).execute().use { }
        }
    }
}
