package com.example.weargithubstore.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/**
 * App theme wrapper.
 *
 * Uses the official Wear OS Material Design theme (Material Design for Wear OS).
 * The defaults automatically adapt to the watch's system color scheme. To apply
 * a branded palette, pass a custom [androidx.wear.compose.material.Colors] to
 * [MaterialTheme]; to adopt Material 3 for Wear OS, swap this for
 * `androidx.wear.compose.material3.MaterialTheme`.
 */
@Composable
fun WearGitHubStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
