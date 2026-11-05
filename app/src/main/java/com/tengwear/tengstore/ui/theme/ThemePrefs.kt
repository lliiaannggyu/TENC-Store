package com.tengwear.tengstore.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 主题种子色的持久化 + 全局可观察状态（移植自 AppOpsPro 的 ThemePrefs.kt）。
 *
 * [current] 是 Compose 快照状态：主题读它 → 用户改选后整棵树自动重组换色，无需重启。
 * 默认值 = [ThemeSeed.Default]（默认中性灰）；旧数据 / 未知 id 也回落到它。
 */
object ThemePrefs {

    private const val PREF_NAME = "tengstore_prefs"
    private const val KEY_SEED = "theme_seed"

    @Volatile
    private var context: Context? = null

    /** 当前选中的配色；出厂默认 = [ThemeSeed.Default]（默认中性灰）。 */
    var current: ThemeSeed by mutableStateOf(ThemeSeed.Default)
        private set

    /** 应用启动时调用一次：注入 context 并载入已保存的选择。 */
    fun init(context: Context) {
        this.context = context.applicationContext
        current = ThemeSeed.fromId(
            try {
                prefs()?.getString(KEY_SEED, null)
            } catch (e: Exception) {
                null
            }
        )
    }

    /** 选择配色，并落盘。 */
    fun select(seed: ThemeSeed) {
        current = seed
        try {
            prefs()?.edit()?.putString(KEY_SEED, seed.id)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun prefs() = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
