package com.tengwear.tengstore.ui.util

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 仓库应用图标解析：优先从仓库根目录探测常见图标文件
 * （icon.png / logo.png 等，走 raw.githubusercontent.com，不占 GitHub API 配额），
 * 探测不到时回退到仓库所有者的 GitHub 头像。
 * 结果按 owner/repo 缓存，一次会话内每个仓库只探测一次。
 */
object RepoIconResolver {

    /** fullName → 图标 URL（含头像回退，永不为空）。 */
    private val cache = ConcurrentHashMap<String, String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /** 仓库根目录常见图标路径，按命中概率排序。 */
    private val CANDIDATES = listOf(
        "icon.png", "logo.png", "icon.jpg", "logo.jpg", "icon.webp", "logo.webp",
        "app_icon.png", "appicon.png", "icon-512.png", "icon-192.png",
        "assets/icon.png", "art/icon.png", "img/icon.png", "images/icon.png",
    )

    suspend fun resolve(fullName: String): String {
        cache[fullName]?.let { return it }
        return withContext(Dispatchers.IO) {
            val owner = fullName.substringBefore("/")
            var found = ""
            if (owner.contains("/") || fullName.contains("/")) {
                val repo = fullName.substringAfter("/", "")
                if (owner.isNotBlank() && repo.isNotBlank()) {
                    for (path in CANDIDATES) {
                        val url = "https://raw.githubusercontent.com/$owner/$repo/HEAD/$path"
                        if (exists(url)) {
                            found = url
                            break
                        }
                    }
                }
            }
            // 回退：仓库所有者的头像（GitHub 头像直链）
            val result = found.ifEmpty {
                if (owner.isNotBlank() && !owner.contains("/")) "https://github.com/$owner.png?size=128" else ""
            }
            if (result.isNotEmpty()) cache[fullName] = result
            result
        }
    }

    /** 探测 URL 是否存在：先 HEAD，方法不被支持时退回 GET。 */
    private fun exists(url: String): Boolean = runCatching {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            when {
                resp.isSuccessful -> true
                resp.code == 404 -> false
                else ->
                    client.newCall(Request.Builder().url(url).build()).execute()
                        .use { it.isSuccessful }
            }
        }
    }.getOrDefault(false)
}

/**
 * 列表条目用的仓库应用图标：解析到图标 / 头像就显示网络图，
 * 还在解析或拿不到时显示占位图标。圆角方形裁剪，观感与应用图标一致。
 */
@Composable
fun RepoIcon(
    fullName: String,
    modifier: Modifier = Modifier,
    fallback: ImageVector = Icons.Default.Android,
) {
    var url by remember(fullName) { mutableStateOf<String?>(null) }
    LaunchedEffect(fullName) {
        if (fullName.contains("/")) url = RepoIconResolver.resolve(fullName)
    }
    if (url.isNullOrEmpty()) {
        Icon(fallback, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(
            model = url,
            contentDescription = "$fullName 图标",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
        )
    }
}
