package com.tengwear.tengstore

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tengwear.tengstore.ui.WearApp
import com.tengwear.tengstore.ui.util.DeepLink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 冷启动深链：tencstore://app/...（其他应用点更新/反馈/授权请求拉起）
        DeepLink.handle(intent?.data?.toString())
        // 下载完成提醒需要通知权限（Android 13+ 运行时权限，重复请求系统会忽略）
        com.tengwear.tengstore.data.download.DownloadService.requestPermission(this)
        setContent {
            WearApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 热启动深链：应用已在前台/后台时再次被拉起
        DeepLink.handle(intent.data?.toString())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ 读剪贴板要求窗口持有输入焦点（没有"粘贴板权限"可申请）；
        // onWindowFocusChanged(true) 是唯一可靠的读取时机，onResume 会读不到。
        if (hasFocus) {
            com.tengwear.tengstore.ui.util.ShareHelper.checkClipboardOnFocus(this)
        }
    }
}
