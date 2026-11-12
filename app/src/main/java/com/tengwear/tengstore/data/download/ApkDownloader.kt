package com.tengwear.tengstore.data.download

import android.content.Context
import android.os.Environment
import com.tengwear.tengstore.domain.model.Asset
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 应用内流式下载器（替代系统 DownloadManager）：
 *
 * 系统 DownloadManager 是黑盒——GitHub 的下载 CDN（objects.githubusercontent.com）
 * 在部分网络下直连极慢或无法建立连接，系统下载器会长期停在「等待中 / 0 字节」。
 *
 * 下载提速（分段并行）：
 * - 先 HEAD 探测：服务器支持 Range 且文件够大时，切成 [SEGMENTS] 段并行下载，
 *   各段直接写入同一文件的对应偏移（RandomAccessFile），段数越多越快；
 * - 服务器不支持断点续传或文件很小时，退回单连接流式下载；
 * - 任何一段失败 → 整条通道作废，自动切到下一个镜像重试；
 * - 进度按所有段已写入字节合计实时回调，进度条真实移动；
 * - 先写 .part 临时文件，完成后原子改名，避免残留半截 APK。
 */
class ApkDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
        val name = sanitize(asset.name)
        val target = File(downloadDir(), name)
        val partial = File(downloadDir(), "$name.part")
        var lastError: Exception? = null

        for (url in candidateUrls(asset.downloadUrl)) {
            try {
                partial.parentFile?.mkdirs()
                if (partial.exists()) partial.delete()
                if (target.exists()) target.delete()

                val (acceptRanges, probedTotal) = probe(url)
                if (acceptRanges && probedTotal > SEGMENT_THRESHOLD) {
                    segmentedDownload(url, probedTotal, partial, onProgress)
                } else {
                    streamDownload(url, partial, onProgress)
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

    /** HEAD 探测：是否支持断点续传 + 文件总大小。 */
    private fun probe(url: String): Pair<Boolean, Long> {
        client.newCall(Request.Builder().url(url).head().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val ranges = resp.header("accept-ranges")?.contains("bytes") == true
            return ranges to (resp.body?.contentLength() ?: -1L)
        }
    }

    /** 单连接流式下载（小文件 / 不支持 Range 的通道兜底）。 */
    private fun streamDownload(url: String, partial: File, onProgress: (Long, Long) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("空响应")
            val total = body.contentLength()
            body.byteStream().copyInto(partial, onProgress, total)
        }
    }

    /** 分段并行下载：每段写自己的偏移，任意段失败抛异常整通道重试。 */
    private suspend fun segmentedDownload(
        url: String,
        total: Long,
        partial: File,
        onProgress: (Long, Long) -> Unit,
    ) = coroutineScope {
        val segments = if (total > 8L * 1024 * 1024) SEGMENTS + 2 else SEGMENTS
        val chunk = (total + segments - 1) / segments
        val written = AtomicLong(0L)

        // 进度上报协程：每 150ms 汇总一次
        val reporter = launch {
            while (isActive) {
                delay(150)
                onProgress(written.get(), total)
            }
        }
        try {
            (0 until segments).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunk
                    val end = minOf(total, start + chunk) - 1
                    val got = downloadSegment(url, start, end, partial)
                    written.addAndGet(got)
                }
            }.awaitAll()
        } finally {
            reporter.cancel()
        }
        onProgress(total, total)
        if (partial.length() != total) throw IOException("分段下载校验失败")
    }

    /** 下载 [start, end] 区间写入 [partial] 的对应偏移；失败自动重试一次。 */
    private fun downloadSegment(url: String, start: Long, end: Long, partial: File): Long {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                client.newCall(
                    Request.Builder().url(url).header("Range", "bytes=$start-$end").build(),
                ).execute().use { resp ->
                    if (resp.code != 206) throw IOException("分段 HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("空响应")
                    val expect = end - start + 1
                    RandomAccessFile(partial, "rw").use { raf ->
                        raf.seek(start)
                        var written = 0L
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                written += n
                            }
                        }
                        if (written != expect) throw IOException("分段长度不符")
                        return written
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                // 重试时直接重写同一段区间（写入位置固定，长度一致可覆盖）
            }
        }
        throw lastError ?: IOException("分段下载失败")
    }

    /** 把流写入文件并按步长回调进度。 */
    private fun InputStream.copyInto(
        file: File,
        onProgress: (Long, Long) -> Unit,
        total: Long,
    ) {
        java.io.FileOutputStream(file).use { out ->
            val buf = ByteArray(64 * 1024)
            var soFar = 0L
            var lastReport = -REPORT_STEP
            while (true) {
                val n = read(buf)
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
        /** 进度上报步长：每 96KB 回调一次。 */
        private const val REPORT_STEP = 96L * 1024

        /** 分段下载的最低门槛：小于 512KB 没必要切。 */
        private const val SEGMENT_THRESHOLD = 512L * 1024

        /** 并行分段数：段数越多越快，单文件超过 8MB 时再 +2。 */
        private const val SEGMENTS = 4

        /** 镜像加速节点（GitHub 前缀代理，直连失败时兜底）。 */
        private val MIRRORS = listOf("https://gh-proxy.com/", "https://ghfast.top/")

        /** Make a filesystem-safe file name from an asset name. */
        fun sanitize(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
