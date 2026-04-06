package com.jntuh.capfit.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class WrapContentRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val expandedHeight = MeasureSpec.makeMeasureSpec(
            Int.MAX_VALUE shr 2,
            MeasureSpec.AT_MOST
        )
        super.onMeasure(widthSpec, expandedHeight)
    }
}