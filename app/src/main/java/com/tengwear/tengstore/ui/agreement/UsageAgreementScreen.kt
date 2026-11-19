package com.tengwear.tengstore.ui.agreement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.tengwear.tengstore.ui.util.LIST_CHIP_HEIGHT
import com.tengwear.tengstore.ui.util.RotaryScroll
import com.tengwear.tengstore.ui.util.edgeSpacers

/**
 * 使用协议页（软件开头展示）：
 *  - 首次启动（或未确认过协议）时进入本页；
 *  - 必须滑动阅读到协议底部，确认按钮才可用；
 *  - 确认后记录标记，之后启动直接进入主页。
 */
@Composable
fun UsageAgreementScreen(onAgreed: () -> Unit) {
    val listState = rememberScalingLazyListState()
    RotaryScroll(listState)

    // 是否已滑动到协议底部：最后一个条目出现在可见区域内
    val reachedBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.totalItemsCount - 1
            info.visibleItemsInfo.any { it.index == last }
        }
    }
    var confirming by remember { mutableStateOf(false) }

    ScreenScaffold(
        scrollState = listState,
        timeText = { TimeText() },
    ) {
        ScalingLazyColumn(state = listState, autoCentering = null, modifier = Modifier.fillMaxSize()) {
            edgeSpacers()
            item { ListHeader { Text("使用协议") } }

            item {
                Text(
                    "欢迎使用 TENC Store。请在使用前仔细阅读本协议，滑到底部后即可确认；确认即表示您已阅读并同意全部条款。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("一、服务说明") } }
            item {
                Text(
                    "1. TENC Store 是一款面向 Wear OS 手表的第三方应用分发工具，内容来源为 GitHub 上公开的开发者仓库与 Release 发布页。\n" +
                        "2. 本软件免费提供，仅提供浏览、下载与安装入口，不参与任何应用的研发、编译与定价。\n" +
                        "3. 软件功能可能随版本更新而调整，更新公告会在应用内公示。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("二、第三方内容") } }
            item {
                Text(
                    "1. 收录的应用均由第三方开发者发布，其功能、稳定性与安全性由对应开发者负责。\n" +
                        "2. 您在安装第三方应用前应自行评估风险，并确认来源可信。\n" +
                        "3. 如收录内容侵犯您的权益，可通过应用内反馈渠道联系我们，核实后会尽快下架。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("三、账号与令牌") } }
            item {
                Text(
                    "1. 登录 GitHub 令牌属于您本人所有，仅保存在手表本地，不会上传或同步到任何服务器。\n" +
                        "2. 令牌仅用于您主动发起的操作（如搜索、收录、上传、快捷投稿收录等权限校验）。\n" +
                        "3. 翻译功能由您自行申请并填写翻译服务令牌（彩云小译），额度消耗在您自己的账户；应用不内置任何令牌，未配置时不会发起翻译请求。\n" +
                        "4. 请妥善保管令牌，如账号出现异常请及时在对应官网撤销授权。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("四、反馈与投稿") } }
            item {
                Text(
                    "1. 反馈与投稿功能通过电子邮件发送，发送前会向您展示内容，邮件包含项目链接与您填写的文字。\n" +
                        "2. 投稿由维护者人工审核，收录结果会以邮件与应用内信息通知。\n" +
                        "3. 请勿通过反馈渠道发送违法、侵权或与项目无关的内容。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("五、本地数据") } }
            item {
                Text(
                    "1. 收藏、历史、下载记录等均保存在手表本地，卸载应用即随之删除。\n" +
                        "2. 本软件不采集、不上传您的个人身份信息。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("六、免责声明") } }
            item {
                Text(
                    "1. 本软件按现状提供，不对服务的持续性、无差错作出保证。\n" +
                        "2. 对因使用或无法使用本软件、或安装第三方应用造成的任何直接或间接损失，在法律允许的最大范围内不承担责任。\n" +
                        "3. 请从官方渠道获取本软件的更新，使用非官方渠道分发的安装包风险自负。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item { ListHeader { Text("七、协议生效") } }
            item {
                Text(
                    "1. 本协议自您点击「同意」起生效，适用于之后使用的全部功能。\n" +
                        "2. 协议内容如有修订，会随新版本重新展示，届时需再次确认。",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item {
                Text(
                    if (reachedBottom) "已阅读至底部，感谢您的耐心。" else "↑ 请继续向下滑动，阅读到协议底部后才能确认",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            item {
                Button(
                    onClick = { if (!confirming) { confirming = true; onAgreed() } },
                    enabled = reachedBottom && !confirming,
                    modifier = Modifier.fillMaxWidth().height(LIST_CHIP_HEIGHT),
                    colors = if (reachedBottom) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Text(if (reachedBottom) "同意并继续" else "滑到底部后可确认", maxLines = 1)
                }
            }
        }
    }
}
