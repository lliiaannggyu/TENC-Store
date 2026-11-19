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
        setContent {
            WearApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 热启动深链：应用已在前台/后台时再次被拉起
        DeepLink.handle(intent.data?.toString())
    }
}
