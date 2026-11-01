package com.tengwear.tengstore.data.download

import android.content.Context
import android.os.Environment
import com.tengwear.tengstore.domain.model.Asset
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 应用内流式下载器（替代系统 DownloadManager）：
 *
 * 系统 DownloadManager 是黑盒——GitHub 的下载 CDN（objects.githubusercontent.com）
 * 在部分网络下直连极慢或无法建立连接，系统下载器会长期停在「等待中 / 0 字节」，
 * 导致进度条纹丝不动。
 *
 * 改为 OkHttp 流式下载：
 * - 进度按实际写入字节数实时回调，进度条真实移动；
 * - 直连失败自动依次尝试镜像加速节点；
 * - 先写 .part 临时文件，完成后原子改名，避免残留半截 APK。
 */
class ApkDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 下载 [asset] 到应用下载目录，通过 [onProgress] 回调 (已下载字节, 总字节)。
     * 总字节未知时为 -1。返回成功后的本地文件。
     */
    suspend fun download(
        asset: Asset,
        onProgress: (soFar: Long, total: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = File(downloadDir(), sanitize(asset.name))
        val partial = File(downloadDir(), sanitize(asset.name) + ".part")
        var lastError: Exception? = null

        for (url in candidateUrls(asset.downloadUrl)) {
            try {
                partial.parentFile?.mkdirs()
                if (partial.exists()) partial.delete()
                if (target.exists()) target.delete()

                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("空响应")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { out ->
                            val buf = ByteArray(16 * 1024)
                            var soFar = 0L
                            var lastReport = -REPORT_STEP
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                soFar += n
                                if (soFar - lastReport >= REPORT_STEP) {
                                    lastReport = soFar
                                    onProgress(soFar, total)
                                }
                            }
                            out.flush()
                            onProgress(soFar, if (total > 0) total else soFar)
                        }
                    }
                }
                if (partial.renameTo(target)) {
                    return@withContext Result.success(target)
                }
                lastError = IOException("无法保存文件")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                partial.delete()
            }
        }
        Result.failure(lastError ?: IOException("所有下载通道均失败"))
    }

    /** 直连优先；失败后依次尝试公共镜像加速（前缀代理）。 */
    private fun candidateUrls(url: String): List<String> =
        listOf(url) + MIRRORS.map { mirror -> mirror + url }

    private fun downloadDir(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir

    /** Resolve the locally saved file for a given asset, if it exists. */
    fun getFile(asset: Asset): File? {
        val target = sanitize(asset.name)
        return downloadDir().listFiles()?.firstOrNull { it.name == target }
    }

    /** All downloaded APK files, newest first. */
    fun listDownloadedApks(): List<File> {
        return downloadDir().listFiles()
            ?.filter { it.name.endsWith(".apk", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    companion object {
        /** 进度上报步长：每 96KB 回调一次（1.6MB 的 APK 约回调 17 次）。 */
        private const val REPORT_STEP = 96L * 1024

        /** 镜像加速节点（GitHub 前缀代理，直连失败时兜底）。 */
        private val MIRRORS = listOf("https://gh-proxy.com/", "https://ghfast.top/")

        /** Make a filesystem-safe file name from an asset name. */
        fun sanitize(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
