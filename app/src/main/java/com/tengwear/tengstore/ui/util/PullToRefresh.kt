package com.tengwear.tengstore.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState

/**
 * 手表「下拉刷新」手势：列表停在顶部时，继续向下拖动超过 [threshold] 即触发 onRefresh。
 *
 * 实现要点：
 *  - 在 [PointerEventPass.Initial] 阶段观察手势，**不 consume 事件**，因此不影响
 *    ScalingLazyColumn 自身的滑动，也不影响表冠旋转滚动（走 OnGenericMotion）。
 *  - 只在列表处于顶部（第一项为 index 0 且未被卷走）时累计下拉距离，避免中间下拉误触发。
 *  - [pullProgress]（0..1）供 UI 显示「松开刷新」提示。
 */
class PullToRefreshState internal constructor() {
    /** 下拉进度 0..1，供 UI 显示提示。 */
    val pullProgress = mutableFloatStateOf(0f)
}

@Composable
fun rememberPullToRefreshState(): PullToRefreshState = remember { PullToRefreshState() }

/**
 * 给列表附加下拉刷新手势。
 * @param listState 列表滚动状态，用于判断是否处于顶部。
 */
fun Modifier.pullToRefresh(
    state: PullToRefreshState,
    listState: ScalingLazyListState,
    threshold: Dp = 72.dp,
    onRefresh: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(onRefresh) {
        val thresholdPx = with(density) { threshold.toPx() }
        awaitEachGesture {
            // Initial 阶段拿第一根手指，不要求未消费——我们只观察，不拦截列表滑动。
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var totalDrag = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dy = change.position.y - change.previousPosition.y
                // 到顶部时 canScrollBackward=false，此时向下拖动才是下拉刷新
                val atTop = !listState.canScrollBackward
                if (atTop && dy > 0f) {
                    totalDrag += dy
                } else if (dy < 0f) {
                    totalDrag += dy // 往回拖动抵消
                }
                if (totalDrag < 0f) totalDrag = 0f
                state.pullProgress.floatValue = (totalDrag / thresholdPx).coerceIn(0f, 1f)
            }
            if (state.pullProgress.floatValue >= 1f) onRefresh()
            state.pullProgress.floatValue = 0f
        }
    },
)
