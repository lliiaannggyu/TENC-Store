package com.tengwear.tengstore.ui.settings

import android.widget.Toast
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
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import com.tengwear.tengstore.ui.util.RotaryScroll
import androidx.compose.material.icons.Icons
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
        }
        AppContainer.log("设置", "已更新设置项：$pendingField")
    }

    fun ask(field: String, label: String) {
        pendingField = field
        launchTextInputSafely(inputLauncher, label)
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
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
                Chip(
                    onClick = { navController.navigate("update") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("检查更新", maxLines = 1) },
                    secondaryLabel = { Text("当前 v${BuildConfig.VERSION_NAME}", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }

            item {
                Chip(
                    onClick = { ask(FIELD_TOKEN, "输入 GitHub 令牌（可选）") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("GitHub 令牌", maxLines = 1) },
                    secondaryLabel = { Text(if (token.isNullOrBlank()) "未设置（60 次/时）" else "已设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                )
            }
            item {
                Chip(
                    onClick = { settings.setToken(null) },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    enabled = !token.isNullOrBlank(),
                    label = { Text("清除令牌", maxLines = 1) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                )
            }

            item { ListHeader { Text("搜索关键词") } }
            item {
                Chip(
                    onClick = { ask(FIELD_KEYWORDS, "输入关键词，用逗号分隔") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("关键词", maxLines = 1) },
                    secondaryLabel = { Text(keywords.ifBlank { "未设置，多个用逗号隔开" }, maxLines = 1) },
                    icon = { Icon(Icons.Default.Tag, contentDescription = null) },
                )
            }
            item {
                ToggleChip(
                    checked = keywordsEnabled,
                    onCheckedChange = { settings.setKeywordsEnabled(it) },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(keywordsEnabled),
                            contentDescription = null,
                        )
                    },
                    label = { Text("启用关键词过滤") },
                )
            }

            item { ListHeader { Text("仓库配置") } }
            item {
                Text(
                    "仓库地址已内置（公告索引、测试项目索引等），无需手动配置。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("开发者") } }
            item {
                Chip(
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
                Chip(
                    onClick = { ask(FIELD_SMTP_CODE, "输入 SMTP 授权码（不是登录密码）") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("SMTP 授权码", maxLines = 1) },
                    secondaryLabel = { Text(if (smtpAuthCode.isBlank()) "未设置（QQ/163 等邮箱后台开启 SMTP 后获取）" else "已设置", maxLines = 1) },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                )
            }
            if (developerEmail.isNotBlank() && smtpAuthCode.isNotBlank()) {
                item {
                    Chip(
                        onClick = {
                            if (verifying) return@Chip
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
                        colors = ChipDefaults.primaryChipColors(),
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
