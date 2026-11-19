package com.tengwear.tengstore.ui.util

import android.content.Context
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tengwear.tengstore.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 仓库名称翻译插件（用户自带额度）：
 *  - 只对接彩云小译 API（https://api.interpreter.caiyunai.com/v1/translator），
 *    应用不内置任何令牌：每次请求都使用用户在设置里填写的_TOKEN_，额度扣用户自己的账户；
 *  - 彩云开放平台注册即送 100 万字符/月免费额度，用户自行申请；
 *  - 结果缓存在本地，同一个仓库名只联网翻译一次，省用户额度；
 *  - 未填写令牌时静默跳过，不发起任何网络请求；
 *  - 按彩云要求标注「数据来自-彩云小译」。
 */
object RepoTranslator {

    private const val PREFS = "repo_name_translations"
    private val prefs by lazy {
        AppContainer.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 取本地缓存（null = 从未成功翻译过）。空字符串 = 暂时失败，不展示。 */
    fun cached(fullName: String): String? = prefs.getString("t:${key(fullName)}", null)

    private fun key(fullName: String): String = fullName.trim().lowercase()

    /** 缓存翻译结果，失败结果带 6 小时冷却，避免反复消耗用户额度。 */
    private fun cache(fullName: String, value: String?, failed: Boolean = false) {
        val v = if (failed) "err:${System.currentTimeMillis()}" else value.orEmpty()
        prefs.edit().putString("t:${key(fullName)}", v).apply()
    }

    private fun cacheUsable(fullName: String): Boolean {
        val v = cached(fullName) ?: return true
        if (!v.startsWith("err:")) return false // 已有译文
        val ts = v.removePrefix("err:").toLongOrNull() ?: return true
        return System.currentTimeMillis() - ts > 6 * 60 * 60 * 1000L
    }

    /** 翻译成简体中文；失败返回 null（界面静默不显示）。 */
    suspend fun translate(fullName: String): String? = withContext(Dispatchers.IO) {
        val token = AppContainer.settings.translateToken.value
        if (token.isNullOrBlank()) return@withContext null // 未配置令牌：不联网
        if (!cacheUsable(fullName)) return@withContext cached(fullName)?.takeIf { !it.startsWith("err:") }
        val name = fullName.substringAfter('/').replace('-', ' ').replace('_', ' ').trim()
        if (name.isBlank() || name.none { it in 'a'..'z' || it in 'A'..'Z' }) {
            return@withContext null // 无英文字母，无需翻译
        }
        val result = runCatching { caiyun(name, token) }.getOrNull()
        if (result.isNullOrBlank() || result.equals(name, ignoreCase = true)) {
            cache(fullName, null, failed = true)
            null
        } else {
            cache(fullName, result)
            result
        }
    }

    /** 彩云小译：令牌由用户提供，只消耗用户自己的免费额度。 */
    private fun caiyun(text: String, token: String): String {
        val body = JSONObject().apply {
            put("source", org.json.JSONArray().put(text))
            put("trans_type", "auto2zh")
            put("detect", true)
            put("media", "text")
            put("request_id", "tenc-store")
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder()
            .url("https://api.interpreter.caiyunai.com/v1/translator")
            .header("Content-Type", "application/json")
            .header("X-Authorization", "token ${token.trim()}")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string().orEmpty())
            if (json.optInt("rc", -1) != 0) {
                throw IllegalStateException(json.optString("msg", "翻译失败"))
            }
            return json.optJSONArray("target")?.optString(0).orEmpty()
        }
    }
}

/** 在仓库名下方显示中文译名（用户配置令牌后才请求；结果本地缓存，按彩云要求标注来源）。 */
@Composable
fun RepoNameTranslation(fullName: String) {
    val token by AppContainer.settings.translateToken.collectAsState()
    var translated by remember(fullName) { mutableStateOf(RepoTranslator.cached(fullName)) }
    LaunchedEffect(fullName, token) {
        if (token.isNullOrBlank()) {
            translated = null
        } else if (translated == null) {
            translated = RepoTranslator.translate(fullName)
        }
    }
    val value = translated
    if (!token.isNullOrBlank() && !value.isNullOrBlank() && !value.startsWith("err:")) {
        Text(
            "译：$value（数据来自-彩云小译）",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
