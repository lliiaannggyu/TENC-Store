package com.tengwear.tengstore.ui.util

import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

/**
 * Shared result key for the Wear OS remote-input flow.
 */
const val REMOTE_INPUT_KEY = "wear_gh_store_input"

/**
 * Builds an intent that opens the Wear OS IME (voice / keyboard / emoji) so the
 * user can type on the watch. This is the official Wear OS text-input pattern.
 */
fun buildTextInputIntent(label: String): Intent {
    val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
        .setLabel(label)
        .wearableExtender {
            setEmojisAllowed(false)
            setInputActionType(EditorInfo.IME_ACTION_DONE)
        }
        .build()
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
    return intent
}

/**
 * 启动文本输入（结果可靠优先）：
 * 直接使用内置 [TextInputActivity]（普通 EditText + 系统输入法），
 * 它在任何设备上都能正确带回输入结果。
 * 之前优先走官方 RemoteInput，但部分手表/输入法上 RemoteInput
 * 会"正常打开却返回空结果"，导致令牌保存不上、搜索没有反应；
 * 因此这里改为内置输入页优先，RemoteInput 仅在内置页启动失败时兜底。
 */
fun launchTextInputSafely(
    launcher: ActivityResultLauncher<Intent>,
    label: String,
    prefill: String? = null,
) {
    try {
        launcher.launch(TextInputActivity.intent(label, prefill))
    } catch (e: Exception) {
        launcher.launch(buildTextInputIntent(label))
    }
}

/**
 * 统一解析输入结果：先按官方 RemoteInput 解析，再尝试内置输入页返回值。
 */
fun parseTextInputResult(data: Intent?): String? {
    parseRemoteInput(data)?.let { return it }
    return data?.getStringExtra(TextInputActivity.EXTRA_VALUE)
        ?.trim()?.takeIf { it.isNotBlank() }
}

/** Extracts the typed text from the activity result of [buildTextInputIntent]. */
fun parseRemoteInput(data: Intent?): String? {
    if (data == null) return null
    val results = RemoteInput.getResultsFromIntent(data) ?: return null
    return results.getCharSequence(REMOTE_INPUT_KEY)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}
