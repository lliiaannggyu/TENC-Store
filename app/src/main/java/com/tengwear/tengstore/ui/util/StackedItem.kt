package com.tengwear.tengstore.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScrollProgress
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

// ── 堆叠层次感（stacked elevation）──────────────────────────────────────
// 移植自 AppOpsPro 的 StackedShadow.kt：给被叠住的上方卡片叠投影阴影——
// 卡片越往上滚出，投影越重，视觉上"浮"起来压住下面的卡片；
// 从下方新滑出来的卡片保持零阴影。

/** 卡片滚到屏顶时的最大阴影高度（dp 值）。深色底上黑色阴影偏弱，故取值偏大。 */
private const val STACKED_SHADOW_MAX_ELEVATION = 18f

/**
 * 堆叠层次感：被叠住的上方卡片加投影阴影、下方保持不变。
 * 必须在 [TransformingLazyColumn] 的 item 内部调用（需要 ItemScope 的 scrollProgress）。
 */
fun TransformingLazyColumnItemScope.stackedShadow(
    shape: Shape,
    shadowColor: Color = Color.Black,
    maxElevation: Float = STACKED_SHADOW_MAX_ELEVATION,
): Modifier {
    val scope = this
    return Modifier.graphicsLayer {
        val progress = scope.run { this@graphicsLayer.scrollProgress }
        val top = resolveTopFraction(progress)
        shadowElevation = if (top < 0f) 0f else (shadowStrength(top) * maxElevation).dp.toPx()
        this.shape = shape
        clip = false
        ambientShadowColor = shadowColor
        spotShadowColor = shadowColor
    }
}

/** 由「卡片顶部相对视口比例」解出阴影强度（0..1，单向上方）。 */
private fun shadowStrength(top: Float): Float {
    val rise = ((0.42f - top) / 0.42f).coerceIn(0f, 1f)
    return rise * rise * (3f - 2f * rise)
}

/** 从官方进度值解出「卡片顶部相对视口的比例」；不可用时返回 -1f。 */
private fun resolveTopFraction(p: TransformingLazyColumnItemScrollProgress): Float {
    if (p.isUnspecified) return -1f
    val t = p.topOffsetFraction
    return if (t.isFinite()) t else -1f
}

// ── 条形胶囊卡片（BarCapsule）───────────────────────────────────────────
// 移植自 AppOpsPro 的 AppBarItem.kt：自绘跑道形条目卡，不用自带容器绘制的
// M3E Button，堆叠过渡（transformedHeight 收窄 + 变暗）才够平滑。

/** 跑道形（Stadium）：percent 50 保证任何高度下两端都是完整半圆。 */
val BarCapsuleShape: Shape = RoundedCornerShape(percent = 50)

/** 条形胶囊高度：与 M3E 条形卡片一致（52dp）。 */
val BarCapsuleHeight = 52.dp

/**
 * 自绘条形胶囊卡片（堆叠列表的标准条目）。
 *
 * @param containerColor 胶囊底色（堆叠列表常规条目用 surfaceContainerHigh）
 * @param contentColor 主内容色（图标 / 主文字）
 * @param secondaryContentColor 次要内容色（副标题）
 * @param onClick 整卡点击回调（null 则不可点击）
 */
@Composable
fun TransformingLazyColumnItemScope.BarCapsule(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    secondaryContentColor: Color = contentColor,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
    secondaryLabel: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Row(
        modifier = modifier
            // 堆叠层次感：底色之前叠投影，沿卡片轮廓投射、不压内容
            .then(stackedShadow(BarCapsuleShape))
            .fillMaxWidth()
            .height(BarCapsuleHeight)
            .clip(BarCapsuleShape)
            .background(containerColor)
            .then(clickModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides contentColor) { leading() }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leading != null) 8.dp else 0.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) { label() }
            if (secondaryLabel != null) {
                CompositionLocalProvider(LocalContentColor provides secondaryContentColor) {
                    secondaryLabel()
                }
            }
        }

        if (trailing != null) {
            Box(modifier = Modifier.padding(start = 6.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides contentColor) { trailing() }
            }
        }
    }
}

/** 堆叠列表标准条目的便捷封装：中性灰底（surfaceContainerHigh）。 */
@Composable
fun TransformingLazyColumnItemScope.StackedCapsule(
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
    secondaryLabel: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        onClick = onClick,
        leading = leading,
        label = label,
        secondaryLabel = secondaryLabel,
    )
}
