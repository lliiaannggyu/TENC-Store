package com.tengwear.tengstore.ui.theme

import androidx.compose.runtime.Composable
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
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
