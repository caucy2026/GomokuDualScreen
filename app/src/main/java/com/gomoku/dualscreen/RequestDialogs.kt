package com.gomoku.dualscreen

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 对战里的“请求确认”弹窗（重新开始 / 悔棋 / 退出）统一走这里。
 *
 * 以前主屏用系统 AlertDialog、副屏用自绘卡片，同一件事在两块屏上长得不一样；
 * 现在两边都用同一套自绘样式，颜色只按请求类型区分。
 */
object RequestDialogs {

    private const val ACCENT_RESTART = "#FF8F00"
    private const val ACCENT_UNDO = "#1565C0"
    private const val ACCENT_EXIT = "#C62828"

    fun restart(activity: Activity, requesterName: String, onResult: (Boolean) -> Unit) = confirm(
        activity,
        title = "重新开始请求",
        message = "${requesterName}请求重新开始\n是否同意？",
        accent = ACCENT_RESTART,
        onResult = onResult
    )

    fun undo(activity: Activity, requesterName: String, onResult: (Boolean) -> Unit) = confirm(
        activity,
        title = "悔棋请求",
        message = "${requesterName}请求悔棋，是否同意？",
        accent = ACCENT_UNDO,
        onResult = onResult
    )

    fun exit(activity: Activity, title: String, message: String, onResult: (Boolean) -> Unit) = confirm(
        activity,
        title = title,
        message = message,
        accent = ACCENT_EXIT,
        onResult = onResult
    )

    private fun confirm(
        activity: Activity,
        title: String,
        message: String,
        accent: String,
        onResult: (Boolean) -> Unit
    ) {
        val container = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        // 同一时刻只留一个确认框，避免连点叠起来。
        container.findViewWithTag<ViewGroup>("request-dialog")?.let { container.removeView(it) }

        val overlay = FrameLayout(activity).apply {
            tag = "request-dialog"
            setBackgroundColor(Color.parseColor("#80000000"))
            isClickable = true
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 30, 40, 30)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.WHITE)
                setStroke(3, Color.parseColor(accent))
            }
        }
        card.addView(TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor(accent))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        card.addView(TextView(activity).apply {
            text = message
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        })
        fun close() {
            if (overlay.parent === container) container.removeView(overlay)
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(button(activity, "同意", "#4CAF50") { close(); onResult(true) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 16 })
        row.addView(button(activity, "不同意", "#F44336") { close(); onResult(false) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        overlay.addView(card, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })
        container.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        overlay.bringToFront()
    }

    private fun button(activity: Activity, text: String, color: String, onClick: () -> Unit): Button =
        Button(activity).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor(color))
            }
            setPadding(24, 14, 24, 14)
            setOnClickListener { onClick() }
        }
}
