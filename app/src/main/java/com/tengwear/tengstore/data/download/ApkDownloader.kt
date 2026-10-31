package com.tengwear.tengstore.data.download

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import com.tengwear.tengstore.domain.model.Asset
import java.io.File

/**
 * Wraps [DownloadManager] to download release artifacts (APKs) into the app's
 * external files directory and to track / list the downloaded files.
 */
class ApkDownloader(private val context: Context) {

    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** Enqueue an APK download; returns the system download id. */
    fun enqueue(asset: Asset): Long {
        val fileName = sanitize(asset.name)
        val request = DownloadManager.Request(android.net.Uri.parse(asset.downloadUrl)).apply {
            setTitle(asset.name)
            setDescription("GitHub release artifact")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        return dm.enqueue(request)
    }

    /**
     * @return download progress in percent (0..100), or null if unknown,
     *         or -1 if the download failed.
     */
    fun queryProgress(downloadId: Long): Int? = querySnapshot(downloadId)?.let {
        when {
            it.done && it.failed -> -1
            it.done -> 100
            else -> it.progress
        }
    }

    /**
     * 下载状态快照：百分比 + 已下载/总字节数。
     * [done] 为 true 表示下载已结束（成功或失败，见 [failed]）。
     */
    data class Snapshot(
        val done: Boolean,
        val failed: Boolean,
        val progress: Int?,
        val soFar: Long,
        val total: Long,
    )

    fun querySnapshot(downloadId: Long): Snapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        dm.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val soFar = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            )
            val progress = if (total <= 0) null else ((soFar * 100) / total).toInt().coerceIn(0, 100)
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> Snapshot(true, false, 100, soFar, total)
                DownloadManager.STATUS_FAILED -> Snapshot(true, true, null, soFar, total)
                else -> Snapshot(false, false, progress, soFar, total)
            }
        }
    }

    /** Resolve the locally saved file for a given asset, if it exists. */
    fun getFile(asset: Asset): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val target = sanitize(asset.name)
        return dir.listFiles()?.firstOrNull { it.name == target }
    }

    /** All downloaded APK files, newest first. */
    fun listDownloadedApks(): List<File> {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.name.endsWith(".apk", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    companion object {
        /** Make a filesystem-safe file name from an asset name. */
        fun sanitize(name: String): String =
            name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
