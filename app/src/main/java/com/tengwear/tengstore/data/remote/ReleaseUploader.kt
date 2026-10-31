package com.tengwear.tengstore.data.remote

import com.tengwear.tengstore.data.AppSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GitHub Release 上传器（应用上架 / 服务端推送的上行通道）：
 * - 用 GitHub 令牌在市场仓库创建 Release（自动建 tag）；
 * - 把 APK 以附件（asset）形式上传，供其他用户从市场下载安装。
 */
class ReleaseUploader(private val settings: AppSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    fun hasToken(): Boolean = !settings.currentToken().isNullOrBlank()

    /**
     * 创建 Release 并返回其 id。tag 不存在时 GitHub 会自动创建。
     * [prerelease] 为 true 时按「测试版」发布（需口令才能在市场解锁）。
     */
    fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        name: String,
        body: String,
        prerelease: Boolean = false,
    ): Long {
        val token = settings.currentToken() ?: error("未设置 GitHub 令牌")
        val json = "{\"tag_name\":\"${jsonEscape(tagName)}\"," +
            "\"name\":\"${jsonEscape(name)}\"," +
            "\"body\":\"${jsonEscape(body)}\"," +
            "\"prerelease\":$prerelease}"
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("创建 Release 失败 (${resp.code})：${text.take(200)}")
            }
            return Regex("\"id\"\\s*:\\s*(\\d+)").find(text)
                ?.groupValues?.get(1)?.toLongOrNull()
                ?: error("无法解析 Release id")
        }
    }

    /** 上传 APK 附件到指定 Release。 */
    fun uploadAsset(owner: String, repo: String, releaseId: Long, file: File) {
        val token = settings.currentToken() ?: error("未设置 GitHub 令牌")
        val url = "https://uploads.github.com/repos/$owner/$repo/releases/$releaseId" +
            "/assets?name=${java.net.URLEncoder.encode(file.name, "UTF-8")}"
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/octet-stream")
            .put(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("上传 APK 失败 (${resp.code})：${resp.body?.string()?.take(200).orEmpty()}")
            }
        }
    }

    private fun jsonEscape(s: String) = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
        .replace("\t", " ")
}
