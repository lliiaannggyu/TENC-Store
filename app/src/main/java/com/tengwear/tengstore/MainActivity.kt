package com.tengwear.tengstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tengwear.tengstore.data.StatsClient
import com.tengwear.tengstore.ui.WearApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }

    override fun onStart() {
        super.onStart()
        // 使用统计：进入前台计一次"打开"，前台期间每 60 秒心跳（在线人数）
        StatsClient.onForeground(this)
    }

    override fun onStop() {
        super.onStop()
        StatsClient.onBackground()
    }
}
