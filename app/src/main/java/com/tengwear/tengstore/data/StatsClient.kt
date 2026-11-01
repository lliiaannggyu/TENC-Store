package com.tengwear.tengstore.data

import android.content.Context
import com.tengwear.tengstore.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** 统计数据快照。 */
data class StoreStats(
    val users: Long,       // 累积用户（去重设备数）
    val online: Long,      // 当前在线（10 分钟内活跃）
    val todayOpens: Long,  // 今日打开次数
    val totalOpens: Long,  // 累计打开次数
)

/**
 * 商店使用统计（LeanCloud 免费开发版，无需自己的服务器/域名）：
 *  - 累积用户：Device 表去重设备（匿名随机 UUID）
 *  - 每日/累计打开：OpenStat 计数表（每次进前台 +1）
 *  - 在线人数：Device.lastActive 最近 10 分钟内的设备数（前台每 5 分钟心跳）
 *
 * 隐私：设备 ID 为随机 UUID，不采集 IMEI/安卓 ID 等任何设备标识符与个人数据。
 * LeanCloud 配置（secrets.properties → BuildConfig）为空时统计完全关闭。
 *
 * 后端配置方法见 stats-worker/README.md「无域名方案：LeanCloud」一节。
 */
object StatsClient {

    private val BASE = BuildConfig.LEANCLOUD_BASE.trimEnd('/')
    private val APP_ID = BuildConfig.LEANCLOUD_ID
    private val APP_KEY = BuildConfig.LEANCLOUD_KEY

    private const val PREF = "tenc_store_stats"
    private const val PREF_DEVICE_ID = "device_id"
    private const val PREF_DEVICE_OBJ = "device_object_id"
    private const val CLASS_DEVICE = "StoreDevice"
    private const val CLASS_STAT = "StoreOpenStat"

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val enabled: Boolean
        get() = BASE.startsWith("http") && APP_ID.isNotBlank() && APP_KEY.isNotBlank()

    /** 匿名设备 ID：首次使用时生成随机 UUID，之后固定不变。 */
    @Synchronized
    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.getString(PREF_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(PREF_DEVICE_ID, id).apply()
        return id
    }

    /** 应用进入前台：计一次"打开"，并开始心跳。 */
    fun onForeground(context: Context) {
        if (!enabled) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching { touchDevice(appContext) }
            runCatching { incrementStat("opens_total") }
            runCatching { incrementStat("opens_${todayCst()}") }
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(5 * 60_000)
                runCatching { touchDevice(appContext) }
            }
        }
    }

    /** 应用退到后台：停止心跳。 */
    fun onBackground() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    /** 拉取统计数据（设置页展示）。返回 null 表示未配置或请求失败。 */
    suspend fun fetchStats(): StoreStats? {
        if (!enabled) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                StoreStats(
                    totalOpens = statValue("opens_total"),
                    todayOpens = statValue("opens_${todayCst()}"),
                    users = deviceCount(),
                    online = deviceCount(10),
                )
            }.getOrNull()
        }
    }

    // ---------- 内部实现（LeanCloud REST API） ----------

    private fun todayCst(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT+8")
        }.format(java.util.Date())

    private fun dateJson(iso: String) = """{"__type":"Date","iso":"$iso"}"""

    private fun nowIso(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

    private fun isoMinutesAgo(minutes: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(System.currentTimeMillis() - minutes * 60_000))

    private fun request(url: String, method: String, body: String? = null): JSONObject? {
        val builder = Request.Builder()
            .url(url)
            .header("X-LC-Id", APP_ID)
            .header("X-LC-Key", APP_KEY)
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonType))
            "PUT" -> builder.put((body ?: "{}").toRequestBody(jsonType))
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return null
            return if (text.isEmpty()) JSONObject() else JSONObject(text)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 查询/创建设备记录并刷新 lastActive；objectId 缓存在本地减少请求数。 */
    private suspend fun touchDevice(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val uuid = deviceId(context)
        val cachedObj = prefs.getString(PREF_DEVICE_OBJ, null)
        val now = dateJson(nowIso())

        if (cachedObj != null) {
            request("$BASE/1.1/classes/$CLASS_DEVICE/$cachedObj", "PUT", """{"lastActive":$now}""")
        } else {
            val found = request(
                "$BASE/1.1/classes/$CLASS_DEVICE?limit=1&where=${enc("""{"uuid":"$uuid"}""")}", "GET",
            )
            val objId = found?.optJSONArray("results")?.optJSONObject(0)?.optString("objectId")
            if (objId != null) {
                request("$BASE/1.1/classes/$CLASS_DEVICE/$objId", "PUT", """{"lastActive":$now}""")
                prefs.edit().putString(PREF_DEVICE_OBJ, objId).apply()
            } else {
                val created = request(
                    "$BASE/1.1/classes/$CLASS_DEVICE", "POST", """{"uuid":"$uuid","lastActive":$now}""",
                )
                created?.optString("objectId")?.takeIf { it.isNotBlank() }?.let {
                    prefs.edit().putString(PREF_DEVICE_OBJ, it).apply()
                }
            }
        }
    }

    /** 打开计数：按 name 找到计数对象后原子自增，不存在则创建。 */
    private suspend fun incrementStat(name: String) = withContext(Dispatchers.IO) {
        val found = request(
            "$BASE/1.1/classes/$CLASS_STAT?limit=1&where=${enc("""{"name":"$name"}""")}", "GET",
        )
        val objId = found?.optJSONArray("results")?.optJSONObject(0)?.optString("objectId")
        if (objId != null) {
            request(
                "$BASE/1.1/classes/$CLASS_STAT/$objId", "POST",
                """{"count":{"__op":"Increment","amount":1}}""",
            )
        } else {
            request("$BASE/1.1/classes/$CLASS_STAT", "POST", """{"name":"$name","count":1}""")
        }
    }

    private suspend fun statValue(name: String): Long = withContext(Dispatchers.IO) {
        val found = request(
            "$BASE/1.1/classes/$CLASS_STAT?limit=1&where=${enc("""{"name":"$name"}""")}", "GET",
        )
        found?.optJSONArray("results")?.optJSONObject(0)?.optLong("count", 0) ?: 0L
    }

    private suspend fun deviceCount(lastActiveWithinMinutes: Long = 0): Long =
        withContext(Dispatchers.IO) {
            val gt = "${'$'}gt"
            val where = if (lastActiveWithinMinutes > 0) {
                """{"lastActive":{"$gt":${dateJson(isoMinutesAgo(lastActiveWithinMinutes))}}}"""
            } else {
                "{}"
            }
            val resp = request(
                "$BASE/1.1/classes/$CLASS_DEVICE?count=1&limit=0&where=${enc(where)}", "GET",
            )
            resp?.optLong("count", 0) ?: 0L
        }
}
