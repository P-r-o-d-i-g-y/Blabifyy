package com.test.blabify.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R

data class ColorMenuItem(
    val id: Int,
    val title: String,
    val iconRes: Int? = null,
    val section: Int = 0 // 0=colors, 1=actions — для разделителей
)

class ColorMenuAdapter(
    private val items: List<ColorMenuItem>,
    private val onClick: (ColorMenuItem) -> Unit
) : RecyclerView.Adapter<ColorMenuAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.itemRoot)
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_color_menu, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.title.text = item.title
        if (item.iconRes != null) {
            h.icon.visibility = View.VISIBLE
            h.icon.setImageResource(item.iconRes)
        } else h.icon.visibility = View.GONE

        // пример: скруглять сильнее крайние пункты секции
        val isFirstInSection = position == 0 || items[position - 1].section != item.section
        val isLastInSection  = position == items.lastIndex || items[position + 1].section != item.section
        h.root.background = h.root.background.mutate().apply {
            // если нужен динамический радиус — можно сделать GradientDrawable и выставлять radii
            // базовый bg_menu_item уже со скруглениями; оставим как есть
        }

        h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}