package com.tengwear.tengstore.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

/**
 * M3E 主题（移植自 AppOpsPro 的 Theme.kt）。
 *
 * 配色优先级（[ThemeSeed]）：
 * 1. [ThemeSeed.System] → 跟随表盘动态取色（Wear OS 4+ / API 33+）
 * 2. [ThemeSeed.Default]（**出厂默认**）→ 默认的中性灰 [defaultWearColorScheme]
 * 3. 其余种子色 → [colorSchemeFromSeed]（整套重新生成，含中性表面）
 */
@Composable
fun WearGitHubStoreTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val seed = ThemePrefs.current
    val colorScheme = when {
        seed == ThemeSeed.System ->
            dynamicColorScheme(context) ?: defaultWearColorScheme()

        // Default 及任何无种子色的项 → 默认中性灰
        seed.seed == null -> defaultWearColorScheme()

        else -> colorSchemeFromSeed(seed.seed!!)
    }
    // 窗口底色跟随当前主题背景色：动态取色/种子色主题下，页面切换与冷启动时
    // 窗口先绘制的底色与 Compose 内容底色一致，消除「闪一下黑背景」。
    DisposableEffect(colorScheme.background) {
        val activity = context as? Activity
        val bg = colorScheme.background
        val argb = android.graphics.Color.argb(
            (bg.alpha * 255).toInt(),
            (bg.red * 255).toInt(),
            (bg.green * 255).toInt(),
            (bg.blue * 255).toInt(),
        )
        activity?.window?.setBackgroundDrawable(ColorDrawable(argb))
        onDispose { }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
