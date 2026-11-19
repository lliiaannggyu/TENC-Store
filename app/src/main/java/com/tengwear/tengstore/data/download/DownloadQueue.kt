package com.tengwear.tengstore.data.download

import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.domain.model.Asset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * 全局下载队列：支持同时把**多个项目、每个项目多个文件**加入队列下载。
 *
 * - 并发上限 [MAX_PARALLEL] 个文件同时下载（每个文件内部仍走 ApkDownloader 分段加速）；
 * - 每个文件独立状态与进度，排队/下载中/成功/失败/取消一目了然；
 * - 进度聚合后写入 [DownloadTracker]，首页下载条与「我的下载」显示整体进度；
 * - 文件按项目子目录存放（owner__repo），避免不同项目同名文件互相覆盖；
 * - 进程内队列，取消/重试/清除均即时生效。
 */
object DownloadQueue {

    enum class State { QUEUED, DOWNLOADING, SUCCESS, FAILED, CANCELLED }

    data class Task(
        val id: Long,
        /** 所属项目 owner/repo（"本地安装"/"更新" 等无项目时可为空串）。 */
        val fullName: String,
        /** 版本标签（可空）。 */
        val tag: String,
        val asset: Asset,
        val state: State = State.QUEUED,
        val progress: Int = 0,
        val soFar: Long = 0L,
        val total: Long = 0L,
        val file: File? = null,
        val error: String? = null,
    ) {
        /** 展示名：项目 + 文件名（多项目时不混淆）。 */
        val label: String get() = if (fullName.isBlank()) asset.name else "$fullName · ${asset.name}"
    }

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val semaphore = Semaphore(MAX_PARALLEL)
    private val seq = AtomicLong(1L)
    private val jobs = mutableMapOf<Long, Job>()
    /** 上次已通知的完成/失败数量（避免重复发完成通知）。 */
    private var lastNotifiedDone = 0
    private var lastNotifiedFailed = 0
    private val downloader: ApkDownloader by lazy { ApkDownloader(AppContainer.context) }

    /** 把同一项目某版本的多个文件批量入队。返回入队数量。 */
    fun enqueue(fullName: String, tag: String, assets: List<Asset>): Int {
        if (assets.isEmpty()) return 0
        val added = assets.map { asset ->
            Task(seq.getAndIncrement(), fullName, tag, asset)
        }
        _tasks.value = _tasks.value + added
        added.forEach { task ->
            jobs[task.id] = AppContainer.appScope.launch {
                semaphore.withPermit {
                    runTask(task)
                }
            }
        }
        startService()
        return added.size
    }

    /** 单文件入队（兼容旧的单下载入口）。 */
    fun enqueueSingle(asset: Asset, fullName: String = "", tag: String = ""): Long {
        val task = Task(seq.getAndIncrement(), fullName, tag, asset)
        _tasks.value = _tasks.value + task
        jobs[task.id] = AppContainer.appScope.launch {
            semaphore.withPermit { runTask(task) }
        }
        startService()
        return task.id
    }

    /** 拉起前台服务：应用退后台后下载继续 + 通知栏进度（UI 入口调用，不受后台启动限制）。 */
    private fun startService() {
        try {
            DownloadService.start(AppContainer.context)
        } catch (_: Exception) {
            // 手表可能限制前台服务启动；下载仍在应用进程内继续
        }
    }

    private suspend fun runTask(task: Task) {
        if (isCancelled(task.id)) return
        update(task.id) { it.copy(state = State.DOWNLOADING) }
        val subDir = task.fullName.takeIf { it.isNotBlank() }
            ?.replace("/", "__")
            ?.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        try {
            val result = downloader.download(task.asset, { soFar, total ->
                val progress = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 100) else 0
                update(task.id) {
                    it.copy(progress = progress, soFar = soFar, total = total, state = State.DOWNLOADING)
                }
                publishAggregate()
            }, subDir = subDir)
            result.fold(
                onSuccess = { file ->
                    update(task.id) {
                        it.copy(state = State.SUCCESS, progress = 100, file = file, error = null)
                    }
                    AppContainer.log("下载", "完成 ${task.label} → ${file.name}")
                },
                onFailure = { e ->
                    update(task.id) {
                        it.copy(state = State.FAILED, error = e.message?.take(80) ?: "网络错误")
                    }
                    AppContainer.log("下载", "失败 ${task.label}：${e.message}")
                },
            )
        } catch (e: CancellationException) {
            update(task.id) { it.copy(state = State.CANCELLED) }
        } catch (e: Exception) {
            update(task.id) { it.copy(state = State.FAILED, error = e.message?.take(80) ?: "未知错误") }
        } finally {
            jobs.remove(task.id)
            publishAggregate()
        }
    }

    private fun isCancelled(id: Long): Boolean =
        _tasks.value.firstOrNull { it.id == id }?.state == State.CANCELLED

    private fun update(id: Long, transform: (Task) -> Task) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
    }

    /** 聚合进度写入 DownloadTracker（首页下载条/我的下载页共用）。 */
    private fun publishAggregate() {
        val current = _tasks.value
        val running = current.filter { it.state == State.QUEUED || it.state == State.DOWNLOADING }
        if (running.isNotEmpty()) {
            val soFar = running.sumOf { it.soFar }
            val total = running.sumOf { it.total }
            val progress = if (total > 0) ((soFar * 100) / total).toInt().coerceIn(0, 99) else 0
            DownloadTracker.update("${running.size} 个文件", progress, soFar, total)
        } else {
            val failed = current.count { it.state == State.FAILED }
            val done = current.count { it.state == State.SUCCESS }
            if (failed == 0) DownloadTracker.finish("$done 个文件下载完成")
            else DownloadTracker.finishFailed("$done 个完成，$failed 个失败")
            // 队列空闲：发「下载完成」系统通知（仅结果变化时），并停止前台服务
            if (done > 0 || failed > 0) {
                val changed = done != lastNotifiedDone || failed != lastNotifiedFailed
                lastNotifiedDone = done
                lastNotifiedFailed = failed
                if (changed) {
                    try {
                        DownloadService.onQueueIdle(AppContainer.context, done, failed)
                        AppContainer.context.stopService(
                            android.content.Intent(AppContainer.context, DownloadService::class.java),
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    /** 取消单个任务（排队中取消则不启动，下载中取消则中断连接）。 */
    fun cancel(id: Long) {
        update(id) { it.copy(state = State.CANCELLED) }
        jobs[id]?.cancel()
        jobs.remove(id)
        publishAggregate()
    }

    /** 重试失败/取消的任务。 */
    fun retry(id: Long) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state != State.FAILED && task.state != State.CANCELLED) return
        update(id) { it.copy(state = State.QUEUED, progress = 0, soFar = 0, total = 0, error = null) }
        jobs[id] = AppContainer.appScope.launch {
            semaphore.withPermit { runTask(_tasks.value.first { it.id == id }) }
        }
    }

    /** 清除已完成/失败/取消的记录（不影响进行中的任务）。 */
    fun clearFinished() {
        _tasks.value = _tasks.value.filter {
            it.state == State.QUEUED || it.state == State.DOWNLOADING
        }
        lastNotifiedDone = 0
        lastNotifiedFailed = 0
        DownloadTracker.consumeFinished()
    }

    /** 是否有进行中的任务。 */
    fun hasActive(): Boolean =
        _tasks.value.any { it.state == State.QUEUED || it.state == State.DOWNLOADING }

    private const val MAX_PARALLEL = 2
}
