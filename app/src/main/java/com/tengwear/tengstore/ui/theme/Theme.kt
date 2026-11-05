package com.tengwear.tengstore.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme

/**
 * App 主题：套用 AppOpsPro 的种子配色方案。
 *
 * 用品牌蓝（Material Blue 500，与商店图标同色系）作为种子色，
 * 由 [colorSchemeFromSeed] 生成整套深色 M3E 配色——强调色族 +
 * 带同色系色相的中性表面，观感与 AppOpsPro 的种子配色一致。
 */
private val BRAND_SEED = Color(0xFF2196F3)

@Composable
fun WearGitHubStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorSchemeFromSeed(BRAND_SEED),
        content = content,
    )
}
