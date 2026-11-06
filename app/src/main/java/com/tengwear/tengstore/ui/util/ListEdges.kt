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

/** ScalingLazyColumn 首尾空隙。 */
fun ScalingLazyListScope.edgeSpacers(top: Dp = LIST_EDGE_GAP, bottom: Dp = LIST_EDGE_GAP) {
    item(key = "__edge_top") { Spacer(Modifier.height(top)) }
    item(key = "__edge_bottom") { Spacer(Modifier.height(bottom)) }
}

/** TransformingLazyColumn 首尾空隙。 */
fun TransformingLazyColumnScope.edgeSpacers(top: Dp = LIST_EDGE_GAP, bottom: Dp = LIST_EDGE_GAP) {
    item(key = "__edge_top") { Spacer(Modifier.height(top)) }
    item(key = "__edge_bottom") { Spacer(Modifier.height(bottom)) }
}
