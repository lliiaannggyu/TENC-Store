package com.example.weargithubstore.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Human-readable file size, e.g. 1.4 MB. */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val groups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val idx = groups.coerceIn(0, units.lastIndex)
    return String.format(
        Locale.US,
        "%.1f %s",
        bytes / Math.pow(1024.0, idx.toDouble()),
        units[idx]
    )
}

/** Format an ISO-8601 timestamp (GitHub style) as yyyy-MM-dd. */
fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date: Date = parser.parse(iso) ?: return iso
        val out = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        out.format(date)
    } catch (_: Exception) {
        iso
    }
}
