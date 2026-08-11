package io.github.honggi82.vr3dmobile.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

object Ui {
    const val BACKGROUND = 0xff090d14.toInt()
    const val PANEL = 0xff121925.toInt()
    const val TEXT = 0xffeef5ff.toInt()
    const val MUTED = 0xff96a5ba.toInt()
    const val ACCENT = 0xff62e6c8.toInt()
    const val LINE = 0xff263247.toInt()

    fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radiusDp.toFloat()
            if (strokeColor != null) setStroke(1, strokeColor)
        }

    fun TextView.asPill(backgroundColor: Int = PANEL, foregroundColor: Int = TEXT) {
        setTextColor(foregroundColor)
        background = rounded(backgroundColor, context.dp(999), LINE)
        setPadding(context.dp(14), context.dp(9), context.dp(14), context.dp(9))
        isAllCaps = false
    }

    fun View.setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.setMargins(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
        layoutParams = params
    }
}
