package com.test.blabify.presentation.ui.decorations

import android.graphics.*
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class RoundedSectionDividerDecoration(
    private val isSectionBreak: (position: Int) -> Boolean,
    private val heightPx: Int,
    private val insetStartPx: Int,
    private val insetEndPx: Int,
    private val cornerRadiusPx: Float,
    color: Int
) : RecyclerView.ItemDecoration() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos != RecyclerView.NO_POSITION && isSectionBreak(pos)) {
            outRect.top = heightPx
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION || !isSectionBreak(pos)) continue

            val left = parent.paddingLeft + insetStartPx
            val right = parent.width - parent.paddingRight - insetEndPx
            val top = child.top - heightPx - (child.layoutParams as? RecyclerView.LayoutParams)?.topMargin.orZero()
            val bottom = top + heightPx

            rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            c.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }
    }

    private fun Int?.orZero() = this ?: 0
}