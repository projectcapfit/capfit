package com.jntuh.capfit.helper

import android.graphics.Color

object ColorUtils {
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (255 * alpha).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
