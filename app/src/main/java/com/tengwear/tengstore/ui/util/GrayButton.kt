package com.tengwear.tengstore.ui.util

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme

/**
 * 全应用统一的列表按钮：中性灰底（surfaceContainerHigh，即原堆叠胶囊的灰色）。
 * 所有过往 Button 调用点已统一替换为 [GrayButton]。
 */
@Composable
fun grayButtonColors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ButtonDefaults.buttonColors(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        iconColor = scheme.onSurface,
    )
}

/** 灰色按钮：参数与 Material3 Button 一致，仅默认 colors 换成统一的灰色色阶。 */
@Composable
fun GrayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = grayButtonColors(),
    secondaryLabel: (@Composable RowScope.() -> Unit)? = null,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    label: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        label = label,
        secondaryLabel = secondaryLabel,
        icon = icon,
    )
}
