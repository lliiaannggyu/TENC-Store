package com.tengwear.tengstore.ui.util

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 内置文本输入页（兜底）：
 * 部分设备缺少 Wear 输入法组件，官方 RemoteInput 意图无法打开导致闪退；
 * 本页用最普通的 EditText + 系统输入法实现同样的输入能力，
 * 在任何 Android 设备上都能工作，结果通过 RESULT_OK 返回。
 */
class TextInputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "请输入"
        val prefill = intent.getStringExtra(EXTRA_VALUE).orEmpty()

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(16), dp(24), dp(16), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        val edit = EditText(this).apply {
            hint = "输入内容"
            setText(prefill)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(
            edit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )

        // 键盘上的“完成”等同确定
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit(edit)
                true
            } else {
                false
            }
        }

        root.addView(Button(this).apply {
            text = "确定"
            setOnClickListener { submit(edit) }
        })

        setContentView(root)
        edit.requestFocus()
    }

    private fun submit(edit: EditText) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_VALUE, edit.text.toString()))
        finish()
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
