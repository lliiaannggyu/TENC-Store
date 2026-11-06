package com.tengwear.tengstore.ui.settings

import android.widget.Toast
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.SwitchButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.tengwear.tengstore.BuildConfig
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.MailClient
import com.tengwear.tengstore.ui.theme.ThemePrefs
import com.tengwear.tengstore.ui.theme.ThemeSeed
import com.tengwear.tengstore.ui.theme.contentOn
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.tengwear.tengstore.ui.util.edgeSpacers

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** 设置页：令牌 / 关键词及开关 / 仓库管理（表格 + 左滑删除）/ 主题配色 / 开发者邮箱。 */
@Composable
fun SettingsScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val token by settings.token.collectAsState()
    val keywords by settings.keywords.collectAsState()
    val keywordsEnabled by settings.keywordsEnabled.collectAsState()
    val developerEmail by settings.developerEmail.collectAsState()
    val developerEmailVerified by settings.developerEmailVerified.collectAsState()
    val smtpAuthCode by settings.smtpAuthCode.collectAsState()
    val testHubRepo by settings.testHubRepo.collectAsState()
    val announceRepo by settings.announceRepo.collectAsState()
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

    // 待二次确认的删除项（type, repo）
    var pendingDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    ScreenScaffold(
            scrollState = listState,
        timeText = { TimeText() },
    ) {
        androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
        edgeSpacers()
            item { ListHeader { Text("设置") } }

            // 版本号：用于核对安装的是否为最新包
            item {
                Text(
                    "TENC Store v${BuildConfig.VERSION_NAME}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // 应用内更新（GitHub Release，非强制）
            item {
                Button(
                    onClick = { navController.navigate("update") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("检查更新", maxLines = 1) },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                )
            }

            item {
                Button(
                    onClick = { ask(FIELD_TOKEN, "输入 GitHub 令牌") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("GitHub 令牌", maxLines = 1) },
                    secondaryLabel = { Text(if (token.isNullOrBlank()) "未设置" else "已设置", maxLines = 1) },
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
                    secondaryLabel = { Text(keywords.ifBlank { "未设置" }, maxLines = 1) },
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
                RepoTable(
                    testHubRepo = testHubRepo,
                    announceRepo = announceRepo,
                    onAdd = { isTestHub ->
                        ask(
                            if (isTestHub) FIELD_TEST_HUB else FIELD_ANNOUNCE,
                            if (isTestHub) "输入测试索引仓库 owner/repo" else "输入公告仓库 owner/repo",
                        )
                    },
                    onDelete = { type, repo -> pendingDelete = type to repo },
                )
            }

            item { ListHeader { Text("主题配色") } }
            item { ThemeColorRow() }

            item { ListHeader { Text("开发者") } }
            item {
                Button(
                    onClick = { ask(FIELD_DEV_EMAIL, "输入你的开发者邮箱") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("开发者邮箱", maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            when {
                                developerEmail.isBlank() -> "未设置"
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
                    onClick = { ask(FIELD_SMTP_CODE, "输入 SMTP 授权码") },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    label = { Text("SMTP 授权码", maxLines = 1) },
                    secondaryLabel = { Text(if (smtpAuthCode.isBlank()) "未设置" else "已设置", maxLines = 1) },
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

        // 删除二次确认（全盘删除前的对话框确认）
        pendingDelete?.let { (type, repo) ->
            AlertDialog(
                visible = true,
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除仓库") },
                text = { Text("确定删除$type「$repo」吗？删除后需重新添加。") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (type == "测试应用仓库") settings.setTestHubRepo("")
                            else settings.setAnnounceRepo("")
                            AppContainer.log("仓库管理", "已删除$type：$repo")
                            Toast.makeText(AppContainer.context, "已删除 $repo", Toast.LENGTH_SHORT).show()
                            pendingDelete = null
                        },
                        label = { Text("删除") },
                        colors = ButtonDefaults.buttonColors(),
                    )
                },
                dismissButton = {
                    Button(
                        onClick = { pendingDelete = null },
                        label = { Text("取消") },
                    )
                },
            )
        }
    }
}

/**
 * 仓库管理表格：每行一条仓库（类型 + 地址），左滑露出红色删除按钮，
 * 松手弹出对话框二次确认后才真正删除；末尾两行是添加入口。
 */
@Composable
private fun RepoTable(
    testHubRepo: String,
    announceRepo: String,
    onAdd: (Boolean) -> Unit,
    onDelete: (String, String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow),
    ) {
        if (testHubRepo.isNotBlank()) {
            SwipeDeleteRow(
                label = "测试应用仓库",
                value = testHubRepo,
                icon = { Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp)) },
                deleteContentDesc = "删除测试应用仓库",
                onSwipePastThreshold = { onDelete("测试应用仓库", testHubRepo) },
            )
            TableDivider()
        }
        if (announceRepo.isNotBlank()) {
            SwipeDeleteRow(
                label = "公告仓库",
                value = announceRepo,
                icon = { Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp)) },
                deleteContentDesc = "删除公告仓库",
                onSwipePastThreshold = { onDelete("公告仓库", announceRepo) },
            )
            TableDivider()
        }
        TableActionRow(
            label = "添加测试仓库",
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        ) { onAdd(true) }
        TableDivider()
        TableActionRow(
            label = "添加公告仓库",
            icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        ) { onAdd(false) }
    }
}

/** 表格行之间的分隔线。 */
@Composable
private fun TableDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    )
}

/**
 * 支持左滑删除的表格行：向左拖动时整行平移、右侧露出红色删除区，
 * 越过阈值松手回调 [onSwipePastThreshold]（由调用方弹二次确认）；
 * 未越过阈值或确认取消时回弹复位。
 */
@Composable
private fun SwipeDeleteRow(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    deleteContentDesc: String,
    onSwipePastThreshold: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { SLIDE_THRESHOLD.dp.toPx() }
    val deleteWidthPx = with(density) { SLIDE_DELETE_WIDTH.dp.toPx() }
    var dragTotal by remember { mutableStateOf(0f) }
    var pending by remember { mutableStateOf(false) }
    // 确认框关闭后回弹复位
    val offset by animateFloatAsState(
        targetValue = when {
            dragTotal < -thresholdPx || pending -> -deleteWidthPx
            else -> 0f
        },
        label = "deleteOffset",
    )

    Box(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        // 底层删除区
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0xFFB3261E)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = deleteContentDesc,
                tint = Color.White,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(20.dp),
            )
        }

        // 表层内容行（随左滑平移）
        Row(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .fillMaxWidth()
                .height(46.dp)
                .background(scheme.surfaceContainerLow)
                .pointerInput(value) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragTotal < -thresholdPx) {
                                pending = true
                                onSwipePastThreshold()
                            } else {
                                dragTotal = 0f
                            }
                        },
                        onDragCancel = { dragTotal = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragTotal += dragAmount
                            if (dragTotal > 0f) dragTotal = 0f
                        },
                    )
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
        }
    }
}

/** 表格中的可点击操作行（添加入口）。 */
@Composable
private fun TableActionRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** 主题配色选择行：默认配色 / 跟随系统 / 官方种子色色块，点选即时生效并落盘。 */
@Composable
private fun ThemeColorRow() {
    val current = ThemePrefs.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeSeed.entries.forEach { seed ->
            ThemeDot(
                seed = seed,
                selected = seed == current,
                onClick = { ThemePrefs.select(seed) },
            )
        }
    }
}

@Composable
private fun ThemeDot(seed: ThemeSeed, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        seed == ThemeSeed.System -> scheme.surfaceContainerHigh
        seed == ThemeSeed.Default -> Color(0xFF9E9E9E)
        else -> seed.seed!!
    }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) scheme.primary else scheme.outline.copy(alpha = 0.5f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (seed == ThemeSeed.System) {
            Icon(
                Icons.Default.Palette,
                contentDescription = seed.label,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        } else if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = seed.label,
                tint = contentOn(fill),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** 左滑触发删除确认的位移阈值（px 计算基于 dp）。 */
private const val SLIDE_THRESHOLD = 56

/** 左滑露出删除区的宽度（dp）。 */
private const val SLIDE_DELETE_WIDTH = 64

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
