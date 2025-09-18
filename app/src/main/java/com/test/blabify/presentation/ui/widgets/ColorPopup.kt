package com.test.blabify.presentation.ui.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.presentation.adapters.ColorMenuAdapter
import com.test.blabify.presentation.adapters.ColorMenuItem
import com.test.blabify.presentation.ui.decorations.RoundedSectionDividerDecoration

class ColorPopup(
    private val context: Context,
    private val items: List<ColorMenuItem>,
    private val onItemClick: (ColorMenuItem) -> Unit,
    private val isSectionBreak: (position: Int) -> Boolean
) {
    private var popupWindow: PopupWindow? = null
    private var contentView: View? = null
    private var isDismissing = false

    fun show(anchor: View) {
        val view = LayoutInflater.from(context).inflate(R.layout.view_color_menu, null)
        contentView = view

        // Recycler без лишней переменной
        view.findViewById<RecyclerView>(R.id.menuRecycler).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            adapter = ColorMenuAdapter(items) {
                onItemClick(it)
                dismissWithAnimation()
            }
            addItemDecoration(
                RoundedSectionDividerDecoration(
                    isSectionBreak = isSectionBreak,
                    heightPx = dp(2), //толщина бабл линии
                    insetStartPx = dp(5), //отступы бабл линии
                    insetEndPx = dp(5),
                    cornerRadiusPx = dp(6).toFloat(),
                    color = ContextCompat.getColor(context, android.R.color.white)
                )
            )
        }

        val pw = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            /* focusable = */ true
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)) // фон задаёт layout
            elevation = dp(8).toFloat()
        }
        popupWindow = pw

        // Начальные значения для анимации
        view.alpha = 0f
        view.scaleX = 0.8f
        view.scaleY = 0.8f

        // Показ возле anchor
        val xoff = 0
        val yoff = dp(8)
        pw.showAsDropDown(anchor, xoff, yoff)

        // Анимируем ТОЛЬКО когда view измерился — тогда pivotX не будет = 0
        view.doOnLayout {
            // привяжем "точку складывания" к правому верхнему углу
            view.pivotX = view.measuredWidth.toFloat()
            view.pivotY = 0f

            view.animate()
                .alpha(1f)
                .scaleX(1f).scaleY(1f)
                .setDuration(150)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        // Корректное закрытие по клику вне — с нашей анимацией
        pw.setTouchInterceptor { v, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                v?.performClick() // для Lint
                dismissWithAnimation()
                true
            } else {
                false
            }
        }

        // Back закроет с анимацией
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissWithAnimation()
                true
            } else false
        }
    }

    private fun dismissWithAnimation() {
        if (isDismissing) return
        isDismissing = true

        val view = contentView
        if (view == null) {
            popupWindow?.dismiss()
            isDismissing = false
            return
        }

        view.animate()
            .alpha(0f)
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(120)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                popupWindow?.dismiss()
                isDismissing = false
            }
            .start()
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}