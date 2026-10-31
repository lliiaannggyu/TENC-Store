package com.example.weargithubstore.ui.upload

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.weargithubstore.data.AppContainer
import com.example.weargithubstore.data.MailClient
import com.example.weargithubstore.data.remote.ReleaseUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UploadUiState(
    val tokenReady: Boolean = false,
    val reposLoading: Boolean = false,
    val reposError: String? = null,
    val repos: List<String> = emptyList(),
    val selectedRepo: String? = null,
    val pickedFile: File? = null,
    val note: String = "",
    val confirmed: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val lastReport: String? = null,
)

/**
 * 应用上传（新流程）：
 * 1. 先检查有无授予 GitHub 令牌，没有则提示去设置；
 * 2. 有令牌则拉取自己账号下的仓库列表，选择一个作为发布目标；
 * 3. 通过内置文件管理器选择本地 APK；
 * 4. 填发布说明并确认后，创建 Release 并上传 APK（更新该仓库）。
 */
class UploadViewModel(application: Application) : AndroidViewModel(application) {

    private val uploader = ReleaseUploader(AppContainer.settings)

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    /** 进入页面：检查令牌；有令牌则加载自己的仓库列表。 */
    fun onEnter() {
        val hasToken = !AppContainer.settings.currentToken().isNullOrBlank()
        _uiState.value = _uiState.value.copy(tokenReady = hasToken)
        if (hasToken && _uiState.value.repos.isEmpty()) loadRepos()
    }

    /** 拉取自己账号下的仓库列表。 */
    fun loadRepos() {
        _uiState.value = _uiState.value.copy(reposLoading = true, reposError = null)
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { AppContainer.gitHubRepository.myRepos() }
            }
            result.fold(
                onSuccess = { list ->
                    _uiState.value = _uiState.value.copy(
                        reposLoading = false,
                        repos = list.map { it.fullName },
                    )
                },
                onFailure = { e ->
                    AppContainer.log("上传", "仓库列表拉取失败：${e.message}")
                    _uiState.value = _uiState.value.copy(
                        reposLoading = false,
                        reposError = "拉取仓库列表失败：${e.message?.take(100)}",
                    )
                },
            )
        }
    }

    fun selectRepo(fullName: String) {
        _uiState.value = _uiState.value.copy(selectedRepo = fullName, notice = null, lastReport = null)
    }

    fun selectFile(file: File) {
        _uiState.value = _uiState.value.copy(pickedFile = file, notice = null, lastReport = null)
    }

    fun setNote(value: String) {
        _uiState.value = _uiState.value.copy(note = value, notice = null)
    }

    fun setConfirmed(value: Boolean) {
        _uiState.value = _uiState.value.copy(confirmed = value, notice = null)
    }

    /** 自动按指定格式编写发布说明。 */
    private fun buildNotes(file: File, tagName: String): String {
        val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val state = _uiState.value
        return buildString {
            appendLine("== TENC Store 应用发布 ==")
            appendLine("仓库: ${state.selectedRepo}")
            appendLine("应用文件: ${file.name}")
            appendLine("大小: ${file.length() / 1024} KB")
            appendLine("版本标签: $tagName")
            appendLine("发布时间: ${timeFmt.format(Date())}")
            appendLine("更新说明: ${state.note.ifBlank { "（无）" }}")
            appendLine("来源: TENC Store 手表端上传")
        }
    }

    /** 创建 Release 并上传 APK 到所选仓库。 */
    fun publish() {
        val state = _uiState.value
        val repo = state.selectedRepo
        when {
            !state.tokenReady -> {
                _uiState.value = state.copy(notice = "未检测到 GitHub 令牌，请先到设置中授予")
                return
            }
            repo == null -> {
                _uiState.value = state.copy(notice = "请先选择要更新到的仓库")
                return
            }
            state.pickedFile == null -> {
                _uiState.value = state.copy(notice = "请先用文件管理器选择 APK")
                return
            }
            state.note.isBlank() -> {
                _uiState.value = state.copy(notice = "发布说明不能为空")
                return
            }
            !state.confirmed -> {
                _uiState.value = state.copy(notice = "请先勾选确认后再发布")
                return
            }
        }
        val file = state.pickedFile!!
        val (owner, repoName) = repo!!.split("/", limit = 2).let { it[0] to it[1] }
        val tagName = "app-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())

        _uiState.value = state.copy(busy = true, notice = "正在发布…")
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val releaseId = uploader.createRelease(
                        owner, repoName, tagName, file.nameWithoutExtension,
                        buildNotes(file, tagName),
                        prerelease = false,
                    )
                    uploader.uploadAsset(owner, repoName, releaseId, file)
                }
            }.onSuccess {
                val report = buildNotes(file, tagName) + "\n结果: 发布成功 ✓"
                AppContainer.log("上传", "已发布 $tagName 到 $repo")
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    notice = "发布成功，$repo 已更新",
                    lastReport = report,
                )
            }.onFailure { e ->
                AppContainer.log("上传", "发布失败：${e.message}")
                _uiState.value = _uiState.value.copy(
                    busy = false,
                    notice = "发布失败：${e.message?.take(120)}",
                )
            }
        }
    }

    /**
     * 邮箱托管：应用内直接发邮件，把发布记录发给开发者邮箱。需要邮箱已验证。
     */
    fun sendReportByEmail(context: Context) {
        val state = _uiState.value
        val report = state.lastReport
        val settings = AppContainer.settings
        val email = settings.developerEmail.value
        val authCode = settings.smtpAuthCode.value
        when {
            report == null -> {
                _uiState.value = state.copy(notice = "还没有可托管的发布记录")
            }
            email.isBlank() -> {
                _uiState.value = state.copy(notice = "请先在设置中填写开发者邮箱")
            }
            authCode.isBlank() -> {
                _uiState.value = state.copy(notice = "请先在设置中填写 SMTP 授权码")
            }
            !settings.developerEmailVerified.value -> {
                _uiState.value = state.copy(notice = "开发者邮箱未验证，请先发送验证邮件")
            }
            else -> {
                _uiState.value = state.copy(notice = "正在通过邮箱托管发送…")
                viewModelScope.launch {
                    val err = MailClient.send(
                        from = email,
                        authCode = authCode,
                        to = email,
                        subject = "TENC Store 应用发布记录（托管）",
                        body = report,
                    )
                    if (err == null) {
                        AppContainer.log("上传", "发布记录已邮箱托管至 $email")
                        _uiState.value = _uiState.value.copy(notice = "发布记录已发送到开发者邮箱")
                    } else {
                        AppContainer.log("上传", "邮箱托管发送失败：$err")
                        _uiState.value = _uiState.value.copy(notice = "发送失败：${err.take(80)}")
                    }
                }
            }
        }
    }
}
