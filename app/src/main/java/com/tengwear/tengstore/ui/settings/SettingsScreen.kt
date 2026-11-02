package com.tengwear.tengstore.ui.settings

import android.widget.Toast
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.collectAsState
import com.tengwear.tengstore.BuildConfig
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.MailClient
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** 设置页：令牌 / 关键词及开关 / 开发者邮箱（含验证）。 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val token by settings.token.collectAsState()
    val keywords by settings.keywords.collectAsState()
    val keywordsEnabled by settings.keywordsEnabled.collectAsState()
    val developerEmail by settings.developerEmail.collectAsState()
    val developerEmailVerified by settings.developerEmailVerified.collectAsState()
    val smtpAuthCode by settings.smtpAuthCode.collectAsState()
    val scope = rememberCoroutineScope()
    var verifying by rememberSaveable { mutableStateOf(false) }
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    // 当前等待输入的字段。rememberSaveable：输入页可能压在应用上方，
    // 用 remember 会在页面回收后丢失字段标记，导致"输入了但保存不上"。
    var pendingField by rememberSaveable { mutableStateOf("") }
    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val value = parseTextInputResult(result.data) ?: return@rememberLauncherForActivityResult
        when (pendingField) {
            FIELD_TOKEN -> settings.setToken(value)
            FIELD_KEYWORDS -> settings.setKeywords(value)
            FIELD_DEV_EMAIL -> saveDeveloperEmail(value)
            FIELD_SMTP_CODE -> settings.setSmtpAuthCode(value)
            FIELD_TEST_HUB -> saveManagedRepo(value, isTestHub = true)
            FIELD_ANNOUNCE -> saveManagedRepo(value, isTestHub = false)
        }
        AppContainer.log("设置", "已更新设置项：$pendingField")
    }

    fun ask(field: String, label: String) {
        pendingField = field
        launchTextInputSafely(inputLauncher, label)
    }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("设置") } }

            // 版本号：用于核对安装的是否为最新包
            item {
                Text(
                    "TENC Store v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // 应用内更新（GitHub Release，非强制）
            item {
                Button(
                    onClick = { navController.navigate("update") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("检查更新", maxLines = 1) },
                    secondaryLabel = { Text("当前 v${BuildConfig.VERSION_NAME}", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }

            item {
                Button(
                    onClick = { ask(FIELD_TOKEN, "输入 GitHub 令牌（可选）") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("GitHub 令牌", maxLines = 1) },
                    secondaryLabel = { Text(if (token.isNullOrBlank()) "未设置（60 次/时）" else "已设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { settings.setToken(null) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = !token.isNullOrBlank(),
                    label = { Text("清除令牌", maxLines = 1) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
            }

            item { ListHeader { Text("搜索关键词") } }
            item {
                Button(
                    onClick = { ask(FIELD_KEYWORDS, "输入关键词，用逗号分隔") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("关键词", maxLines = 1) },
                    secondaryLabel = { Text(keywords.ifBlank { "未设置，多个用逗号隔开" }, maxLines = 1) },
                    icon = { Icon(Icons.Default.Tag, contentDescription = null) },
                )
            }
            item {
                SwitchButton(
                    checked = keywordsEnabled,
                    onCheckedChange = { settings.setKeywordsEnabled(it) },
                    label = { Text("启用关键词过滤") },
                )
            }

            item { ListHeader { Text("仓库管理") } }
            item {
                Text(
                    "修改前会用你登录的令牌验证对该仓库的推送权限，应用内不保存任何内置令牌。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item {
                Button(
                    onClick = { ask(FIELD_TEST_HUB, "输入测试索引仓库 owner/repo") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("测试应用仓库", maxLines = 1) },
                    secondaryLabel = { Text(settings.testHubRepo.value.ifBlank { "未配置" }, maxLines = 1) },
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                )
            }
            item {
                Button(
                    onClick = { ask(FIELD_ANNOUNCE, "输入公告仓库 owner/repo") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("公告仓库", maxLines = 1) },
                    secondaryLabel = { Text(settings.announceRepo.value.ifBlank { "未配置" }, maxLines = 1) },
                    icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                )
            }

            item { ListHeader { Text("开发者") } }
            item {
                Button(
                    onClick = { ask(FIELD_DEV_EMAIL, "输入你的开发者邮箱") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("开发者邮箱", maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            when {
                                developerEmail.isBlank() -> "未设置（应用内发邮件托管日志）"
                                developerEmailVerified -> "$developerEmail · 已验证"
                                else -> "$developerEmail · 待验证"
                            },
                            maxLines = 1,
                        )
                    },
                    icon = {
                        Icon(
                            if (developerEmailVerified) Icons.Default.CheckCircle else Icons.Default.Email,
                            contentDescription = null,
                        )
                    },
                )
            }
            item {
                Button(
                    onClick = { ask(FIELD_SMTP_CODE, "输入 SMTP 授权码（不是登录密码）") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("SMTP 授权码", maxLines = 1) },
                    secondaryLabel = { Text(if (smtpAuthCode.isBlank()) "未设置（QQ/163 等邮箱后台开启 SMTP 后获取）" else "已设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                )
            }
            if (developerEmail.isNotBlank() && smtpAuthCode.isNotBlank()) {
                item {
                    Button(
                        onClick = {
                            if (verifying) return@Button
                            verifying = true
                            scope.launch {
                                val err = MailClient.send(
                                    from = developerEmail,
                                    authCode = smtpAuthCode,
                                    to = developerEmail,
                                    subject = "TENC Store 开发者邮箱验证",
                                    body = "这是 TENC Store 的验证邮件。收到即说明应用内发邮件配置成功，日志托管与发布记录托管将发送到此邮箱。",
                                )
                                verifying = false
                                if (err == null) {
                                    settings.setDeveloperEmailVerified(true)
                                    Toast.makeText(AppContainer.context, "验证邮件已发出并成功投递", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(AppContainer.context, "发送失败：${err.take(60)}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        enabled = !verifying,
                        label = { Text("发送验证邮件", maxLines = 1) },
                        secondaryLabel = { Text(if (verifying) "正在发送…" else "应用内直接发送，收到即验证", maxLines = 1) },
                        icon = {
                            if (verifying) {
                                CircularProgressIndicator()
                            } else {
                                Icon(Icons.Default.Email, contentDescription = null)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(),
                    )
                }
            }
        }
    }
}

/** 校验邮箱格式；合法则保存，验证改为在应用内真实发送验证邮件。 */
private fun saveDeveloperEmail(value: String) {
    val email = value.trim()
    val settings = AppContainer.settings
    if (!EMAIL_REGEX.matches(email)) {
        Toast.makeText(AppContainer.context, "邮箱格式不正确，未保存", Toast.LENGTH_SHORT).show()
        AppContainer.log("设置", "开发者邮箱格式不正确：$email")
        return
    }
    settings.setDeveloperEmail(email)
    Toast.makeText(AppContainer.context, "已保存，请填写授权码并发送验证邮件", Toast.LENGTH_SHORT).show()
}

private const val FIELD_TOKEN = "token"
private const val FIELD_KEYWORDS = "keywords"
private const val FIELD_DEV_EMAIL = "developer_email"
private const val FIELD_SMTP_CODE = "smtp_auth_code"
private const val FIELD_TEST_HUB = "test_hub_repo"
private const val FIELD_ANNOUNCE = "announce_repo"

/** 校验输入的 owner/repo，并用当前令牌验证推送权限，通过才保存。 */
private fun saveManagedRepo(value: String, isTestHub: Boolean) {
    val settings = AppContainer.settings
    val repo = value.trim().removePrefix("https://github.com/").removeSuffix("/").trim('/')
    val pattern = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    if (!pattern.matches(repo)) {
        Toast.makeText(AppContainer.context, "格式应为 owner/repo，未保存", Toast.LENGTH_SHORT).show()
        return
    }
    val token = settings.currentToken()
    if (token.isNullOrBlank()) {
        Toast.makeText(AppContainer.context, "请先在设置里登录 GitHub 令牌再修改", Toast.LENGTH_LONG).show()
        return
    }
    val owner = repo.substringBefore("/")
    val name = repo.substringAfter("/")
    AppContainer.appScope.launch {
        val access = AppContainer.gitHubRepository.checkPushAccess(owner, name)
        when (access) {
            true -> {
                if (isTestHub) settings.setTestHubRepo(repo) else settings.setAnnounceRepo(repo)
                AppContainer.log("仓库管理", "已更新${if (isTestHub) "测试" else "公告"}仓库为 $repo（权限验证通过）")
                Toast.makeText(AppContainer.context, "权限验证通过，已保存 $repo", Toast.LENGTH_SHORT).show()
            }
            false -> {
                AppContainer.log("仓库管理", "令牌对 $repo 无推送权限，拒绝保存")
                Toast.makeText(AppContainer.context, "当前令牌对该仓库没有推送权限，未保存", Toast.LENGTH_LONG).show()
            }
            null -> {
                Toast.makeText(AppContainer.context, "无法验证权限（网络问题），未保存", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
