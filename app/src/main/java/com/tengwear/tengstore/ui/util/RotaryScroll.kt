package com.tengwear.tengstore.ui.util

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewConfigurationCompat
import kotlinx.coroutines.launch

/**
 * 三星 Galaxy Watch 虚拟表冠 / 旋转表圈适配。
 *
 * 在 ComposeView 上监听旋转编码器（SOURCE_ROTARY_ENCODER）的 ACTION_SCROLL
 * 事件，把旋转增量换算成像素，交给当前屏幕的 [ScrollableState] 滚动。
 * 同时兼容 [androidx.wear.compose.foundation.lazy.ScalingLazyListState] 与
 * [androidx.wear.compose.foundation.lazy.TransformingLazyColumnState]。
 *
 * 用法：在拥有列表的页面里调用 `RotaryScroll(listState)`。
 */
@Composable
fun RotaryScroll(state: ScrollableState) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollFactor = remember(view) {
        ViewConfigurationCompat.getScaledVerticalScrollFactor(
            ViewConfiguration.get(view.context),
            view.context,
        )
    }
    DisposableEffect(view, state, scrollFactor) {
        val listener = View.OnGenericMotionListener { _, event ->
            if (
                event.action == MotionEvent.ACTION_SCROLL &&
                event.source and InputDevice.SOURCE_ROTARY_ENCODER == InputDevice.SOURCE_ROTARY_ENCODER
            ) {
                val delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * scrollFactor
                scope.launch { state.scroll { scrollBy(delta) } }
                true
            } else {
                false
            }
        }
        view.setOnGenericMotionListener(listener)
        onDispose { view.setOnGenericMotionListener(null) }
    }
}
