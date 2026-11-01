package com.tengwear.tengstore.ui.util

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * 内置文本输入页（重新设计）：
 * 部分设备缺少 Wear 输入法组件，官方 RemoteInput 意图无法打开导致闪退；
 * 本页提供与商店风格一致的输入界面：
 *  - 顶部：说明文字
 *  - 中部：圆角深色输入卡片（聚焦高亮描边，光标与文字醒目）
 *  - 底部：取消 / 确定两个圆形按钮（手表习惯操作）
 * 在任何 Android 设备上都能工作，结果通过 RESULT_OK 返回。
 */
class TextInputActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "请输入"
        val prefill = intent.getStringExtra(EXTRA_VALUE).orEmpty()

        setContent {
            MaterialTheme {
                InputPage(
                    label = label,
                    prefill = prefill,
                    onConfirm = { value ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_VALUE, value))
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_VALUE = "value"

        fun intent(label: String, prefill: String? = null): Intent =
            Intent().setClassName(
                "com.tengwear.tengstore",
                "com.tengwear.tengstore.ui.util.TextInputActivity",
            )
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_VALUE, prefill.orEmpty())
    }
}

/** 输入页主题色（与首页公告标签同色系） */
private val AccentBlue = Color(0xFF3D82F0)
private val CardBg = Color(0xFF232323)
private val CardBgFocused = Color(0xFF2B2B2B)
private val CardStroke = Color(0xFF3A3A3A)
private val CancelBg = Color(0xFF2E2E2E)

@Composable
private fun InputPage(
    label: String,
    prefill: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf(prefill) }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // 进入页面自动聚焦并弹出键盘
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 说明文字
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(14.dp))

            // 输入卡片
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (focused) CardBgFocused else CardBg,
                        RoundedCornerShape(26.dp),
                    )
                    .border(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) AccentBlue else CardStroke,
                        shape = RoundedCornerShape(26.dp),
                    )
                    .padding(horizontal = 18.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (focused) AccentBlue else Color(0xFF8A8A8A),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "输入内容",
                            color = Color(0xFF7A7A7A),
                            fontSize = 15.sp,
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it.replace("\n", "") },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(AccentBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (text.isNotBlank()) onConfirm(text.trim()) },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused }
                            .focusRequester(focusRequester),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 取消 / 确定 圆形按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                RoundActionButton(
                    icon = Icons.Default.Close,
                    contentDesc = "取消",
                    background = CancelBg,
                    tint = Color(0xFFB9B9B9),
                    onClick = onCancel,
                )
                Spacer(Modifier.width(22.dp))
                RoundActionButton(
                    icon = Icons.Default.Check,
                    contentDesc = "确定",
                    background = AccentBlue,
                    tint = Color.White,
                    enabled = text.isNotBlank(),
                    onClick = { onConfirm(text.trim()) },
                )
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: ImageVector,
    contentDesc: String,
    background: Color,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val finalBg = if (enabled) background else background.copy(alpha = 0.35f)
    val finalTint = if (enabled) tint else tint.copy(alpha = 0.4f)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(52.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = finalBg,
            contentColor = finalTint,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = contentDesc, tint = finalTint)
    }
}
