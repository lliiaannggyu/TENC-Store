package com.tengwear.tengstore.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 一条应用日志。 */
data class LogEntry(val time: Long = 0L, val tag: String = "", val message: String = "")

/**
 * 轻量日志中心：记录应用内关键事件（搜索、下载、错误等），
 * 持久化到 SharedPreferences，用于「日志反馈」导出与发送。
 */
class LogStore(private val context: Context) {
    private val gson = Gson()
    private val lock = Any()

    fun log(tag: String, message: String) {
        synchronized(lock) {
            val list = read().toMutableList()
            list.add(LogEntry(System.currentTimeMillis(), tag, message))
            while (list.size > MAX_ENTRIES) list.removeAt(0)
            prefs.edit().putString(KEY_LOGS, gson.toJson(list)).apply()
        }
    }

    fun all(): List<LogEntry> = synchronized(lock) { read() }

    fun clear() = synchronized(lock) { prefs.edit().remove(KEY_LOGS).apply() }

    private fun read(): List<LogEntry> {
        val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            gson.fromJson<List<LogEntry>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private val prefs get() = context.getSharedPreferences("wear_gh_store_logs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_LOGS = "log_entries"
        const val MAX_ENTRIES = 200
    }
}
