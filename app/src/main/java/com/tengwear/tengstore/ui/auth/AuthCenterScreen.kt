package com.tengwear.tengstore.ui.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import com.tengwear.tengstore.data.AppContainer
import com.tengwear.tengstore.data.AppSettings
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.launchTextInputSafely
import com.tengwear.tengstore.ui.util.parseTextInputResult
import com.tengwear.tengstore.ui.util.edgeSpacers
import com.tengwear.tengstore.ui.util.GrayButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 授权中心：商店给本机其他应用签发授权（通用服务，包括但不限于邮箱授权，把绑定邮箱
 * 授权给其他应用当账号使用）。每条授权生成一个加密授权码（复制后可粘贴到被授权应用
 * 录入），授权记录仅存本机，随时可撤销。
 *
 * 授权协议 v2：任意本机应用均可通过深链
 * `tencstore://app/auth/{应用名}?n={nonce}&d={设备指纹}&cb={回调URI}` 发起授权请求
 * （三个参数均可缺省）。确认页锁定应用名；签发成功后若带回调 URI 则免剪贴板直传
 * （回跳请求方应用并附 code），同时保留复制到剪贴板兜底。加密算法与密码本不变。
 */
@Composable
fun AuthCenterScreen(navController: NavHostController) {
    val settings = AppContainer.settings
    val grants by settings.authGrants.collectAsState()
    val developerEmail by settings.developerEmail.collectAsState()
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    // 深链预填的授权请求：进入页面即弹确认（协议 v2：n/d/cb 一并消费后清空）
    val requestedApp = com.tengwear.tengstore.ui.util.DeepLink.pendingAuthApp.value
    var pendingGrantApp by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGrantNonce by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGrantDevice by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingGrantCallback by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(requestedApp) {
        if (requestedApp != null) {
            val dl = com.tengwear.tengstore.ui.util.DeepLink
            pendingGrantApp = requestedApp
            pendingGrantNonce = dl.pendingAuthNonce.value
            pendingGrantDevice = dl.pendingAuthDevice.value
            pendingGrantCallback = dl.pendingAuthCallback.value
            dl.pendingAuthApp.value = null
            dl.pendingAuthNonce.value = null
            dl.pendingAuthDevice.value = null
            dl.pendingAuthCallback.value = null
        }
    }

    /** 关闭待确认的授权请求（丢弃未处理的 nonce/device/callback）。 */
    fun clearPendingGrant() {
        pendingGrantApp = null
        pendingGrantNonce = null
        pendingGrantDevice = null
        pendingGrantCallback = null
    }

    // 刚签发的授权码（弹窗展示 + 复制）——AuthGrant 非 Saveable，用 remember
    var issuedGrant by remember { mutableStateOf<AppSettings.AuthGrant?>(null) }

    var pendingRevoke by remember { mutableStateOf<AppSettings.AuthGrant?>(null) }

    /**
     * 签发授权（通用协议 v2）：
     *  - 手动新增（无深链）：nonce/device/callback 全为 null，行为与旧版一致；
     *  - 深链请求：携带请求方的 nonce/device，签发后若带回调 URI 则
     *    免剪贴板直传（回跳请求方应用并附 code），同时保留剪贴板兜底。
     */
    fun issue(
        appName: String,
        nonce: String? = null,
        device: String? = null,
        callback: String? = null,
    ) {
        val app = appName.trim()
        if (app.isBlank()) {
            Toast.makeText(AppContainer.context, "请先填写要授权的应用名", Toast.LENGTH_SHORT).show()
            return
        }
        if (developerEmail.isBlank()) {
            Toast.makeText(
                AppContainer.context,
                "请先到设置绑定开发者邮箱，才能授权给其他应用",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val grant = settings.addAuthGrant(app, developerEmail, nonce, device)
        issuedGrant = grant
        // 剪贴板兜底：无论是否回跳都复制，保证手动粘贴路径始终可用
        runCatching {
            val cm = AppContainer.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TENC Store 授权码", grant.code))
        }
        AppContainer.log("授权中心", "已授权 $app（邮箱 $developerEmail）")
        // 协议 v2：带回调 URI 时免剪贴板直传授权码——回跳请求方应用
        callback?.takeIf { it.isNotBlank() }?.let { cb ->
            runCatching {
                val sep = if (cb.contains('?')) '&' else '?'
                AppContainer.context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setData(Uri.parse("$cb${sep}code=${Uri.encode(grant.code)}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { AppContainer.log("授权中心", "回调失败：${it.message}") }
        }
    }

    val appInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        parseTextInputResult(result.data)?.let { issue(it) }
    }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
            edgeSpacers()
            item { ListHeader { Text("授权中心") } }

            item {
                Text(
                    "把你的邮箱等信息授权给本机其他应用（当账号使用）。" +
                        "签发后授权码自动复制，粘贴到被授权应用即可；授权只存本机，可随时撤销。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item {
                GrayButton(
                    onClick = {
                        launchTextInputSafely(appInputLauncher, "输入要授权的应用名")
                    },
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text("新增授权", maxLines = 1) },
                    secondaryLabel = { Text("邮箱 → 应用", maxLines = 1) },
                )
            }

            if (grants.isNotEmpty()) {
                item { ListHeader { Text("已授权应用") } }
                grants.forEach { grant ->
                    val time = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
                        .format(Date(grant.createdAt))
                    item(key = "${grant.app}|${grant.createdAt}") {
                        GrayButton(
                            onClick = { pendingRevoke = grant },
                            modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                            icon = { Icon(Icons.Default.Key, contentDescription = null) },
                            label = { Text(grant.app, maxLines = 1) },
                            secondaryLabel = {
                                Text("$time · ${grant.email}", maxLines = 1)
                            },
                        )
                    }
                }
                item {
                    GrayButton(
                        onClick = { pendingRevoke = grants.first() },
                        modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                        enabled = false,
                        icon = { Icon(Icons.Default.Email, contentDescription = null) },
                        label = { Text("点击条目可撤销授权", maxLines = 1) },
                    )
                }
            } else {
                item {
                    Text(
                        "还没有授权记录。其他应用也可通过深链 tencstore://app/auth/{应用名} 发起授权请求。",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    // 授权请求确认（深链发起）：应用名锁定不可编辑，带设备指纹时显示绑定说明
    pendingGrantApp?.let { app ->
        AlertDialog(
            visible = true,
            onDismissRequest = { clearPendingGrant() },
            title = { Text("来自 $app 的授权请求", maxLines = 1) },
            text = {
                Text(
                    buildString {
                        appendLine("「$app」申请使用你的邮箱（$developerEmail）作为账号授权。")
                        pendingGrantDevice?.let { d ->
                            appendLine("设备指纹：${d.take(8)}…（绑定本机设备）")
                        }
                        append(
                            if (pendingGrantCallback != null) {
                                "同意将签发授权码并自动返回应用。"
                            } else {
                                "同意将签发授权码并复制到剪贴板。"
                            },
                        )
                    },
                )
            },
            confirmButton = {
                GrayButton(
                    onClick = {
                        val n = pendingGrantNonce
                        val d = pendingGrantDevice
                        val cb = pendingGrantCallback
                        clearPendingGrant()
                        issue(app, n, d, cb)
                    },
                    label = {
                        Text(
                            if (pendingGrantCallback != null) "确认并自动返回应用" else "同意授权",
                            maxLines = 1,
                        )
                    },
                )
            },
            dismissButton = {
                GrayButton(onClick = { clearPendingGrant() }, label = { Text("拒绝") })
            },
        )
    }

    // 签发结果：展示授权码
    issuedGrant?.let { grant ->
        AlertDialog(
            visible = true,
            onDismissRequest = { issuedGrant = null },
            confirmButton = {
                GrayButton(
                    onClick = {
                        runCatching {
                            val cm = AppContainer.context
                                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("TENC Store 授权码", grant.code))
                            Toast.makeText(AppContainer.context, "已复制授权码", Toast.LENGTH_SHORT).show()
                        }
                        issuedGrant = null
                    },
                    label = { Text("复制授权码") },
                )
            },
            title = { Text("已授权 ${grant.app}", maxLines = 1) },
            text = { Text("授权码已自动复制到剪贴板，粘贴到「${grant.app}」即可完成绑定。") },
        )
    }

    // 撤销确认
    pendingRevoke?.let { grant ->
        AlertDialog(
            visible = true,
            onDismissRequest = { pendingRevoke = null },
            title = { Text("撤销授权", maxLines = 1) },
            text = { Text("撤销「${grant.app}」的授权吗？撤销后其授权码立即失效。") },
            confirmButton = {
                GrayButton(
                    onClick = {
                        settings.removeAuthGrant(grant)
                        pendingRevoke = null
                        Toast.makeText(AppContainer.context, "已撤销授权", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("撤销") },
                )
            },
            dismissButton = {
                GrayButton(onClick = { pendingRevoke = null }, label = { Text("取消") })
            },
        )
    }
}
