package com.tengwear.tengstore.ui.util

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope

/** 圆屏手表列表首尾留出的空隙，避免顶部/底部内容被表圈裁切。 */
val LIST_EDGE_GAP = 28.dp

/** ScalingLazyColumn 尾部空隙（顶部不留，内容直接顶到 TimeText 下方）。 */
fun ScalingLazyListScope.edgeSpacers(top: Dp = 0.dp, bottom: Dp = LIST_EDGE_GAP) {
    if (top > 0.dp) item(key = "__edge_top") { Spacer(Modifier.height(top)) }
    item(key = "__edge_bottom") { Spacer(Modifier.height(bottom)) }
}

/** TransformingLazyColumn 尾部空隙（顶部不留）。 */
fun TransformingLazyColumnScope.edgeSpacers(top: Dp = 0.dp, bottom: Dp = LIST_EDGE_GAP) {
    if (top > 0.dp) item(key = "__edge_top") { Spacer(Modifier.height(top)) }
    item(key = "__edge_bottom") { Spacer(Modifier.height(bottom)) }
}
