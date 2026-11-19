package com.tengwear.tengstore.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tengwear.tengstore.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 下载前台服务：让下载在应用退到后台后继续运行（Wear OS 会杀后台进程，前台服务保活），
 * 同时在通知栏显示聚合进度；队列空闲时发「下载完成」通知并自动停止。
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DownloadQueue.hasActive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIF_PROGRESS,
            progressNotification("准备下载…", 0L, 0L, false),
        )
        collectJob?.cancel()
        collectJob = scope.launch {
            var lastText = ""
            var lastPct = -1
            DownloadQueue.tasks.collect { tasks ->
                val running = tasks.filter {
                    it.state == DownloadQueue.State.QUEUED || it.state == DownloadQueue.State.DOWNLOADING
                }
                if (running.isEmpty()) return@collect
                val soFar = running.sumOf { it.soFar }
                val total = running.sumOf { it.total }
                val current = running.firstOrNull { it.state == DownloadQueue.State.DOWNLOADING }
                val text = "${running.size} 个文件 · ${current?.asset?.name ?: ""}"
                val determinate = total > 0
                val pct = if (determinate) ((soFar * 100) / total).toInt().coerceIn(0, 99) else -1
                // 节流：文案变化或每 5% 更新一次通知
                if (text != lastText || (pct >= 0 && pct / 5 != lastPct / 5)) {
                    lastText = text
                    lastPct = pct
                    notify(NOTIF_PROGRESS, progressNotification(text, soFar, total, determinate))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notify(id: Int, notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun progressNotification(
        text: String,
        soFar: Long,
        total: Long,
        determinate: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在后台下载")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp())
        if (determinate) {
            val pct = ((soFar * 100) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false).setSubText("$pct%")
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_PROGRESS = "download_progress"
        private const val CHANNEL_DONE = "download_done"
        private const val NOTIF_PROGRESS = 41
        private const val NOTIF_DONE = 42

        /** 有新任务入队时调用（UI 上下文）：拉起前台服务。 */
        fun start(context: Context) {
            if (!DownloadQueue.hasActive()) return
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }

        /** 请求通知权限（Android 13+ 运行时权限，UI 上下文调用）。 */
        fun requestPermission(activity: android.app.Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val perm = android.Manifest.permission.POST_NOTIFICATIONS
                if (activity.checkSelfPermission(perm) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    activity.requestPermissions(arrayOf(perm), 7001)
                }
            }
        }

        /** DownloadQueue 在队列空闲时调用：发完成通知、移除进度通知。 */
        fun onQueueIdle(context: Context, done: Int, failed: Int) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.cancel(NOTIF_PROGRESS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                nm.getNotificationChannel(CHANNEL_DONE) == null
            ) {
                createChannels(context)
            }
            val text = when {
                failed == 0 -> "$done 个文件下载完成"
                done == 0 -> "下载失败 $failed 个文件"
                else -> "$done 个完成，$failed 个失败"
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (failed == 0) "下载完成" else "下载有失败")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pi)
            if (failed > 0) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText("$text，打开应用可在「我的下载」重试。"),
                )
            }
            nm.notify(NOTIF_DONE, builder.build())
        }

        private fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_PROGRESS, "下载进度", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "后台下载时的持续进度通知"
                    setSound(null, null)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "下载完成提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "下载结束后的完成/失败提醒"
                },
            )
        }
    }
}
