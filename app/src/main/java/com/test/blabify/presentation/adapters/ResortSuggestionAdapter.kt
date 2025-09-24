package com.test.blabify.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.usecases.ResortSuggestion

class ResortSuggestionAdapter(
    private val items: List<ResortSuggestion>
) : RecyclerView.Adapter<ResortSuggestionAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvFile: TextView = v.findViewById(R.id.tvFile)
        val tvFromTo: TextView = v.findViewById(R.id.tvFromTo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_resort_suggestion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val it = items[position]
        h.tvFile.text = it.fileName ?: "(без имени)"
        h.tvFromTo.text = "${it.fromPath}  →  ${it.toPath}"
    }

    override fun getItemCount(): Int = items.size
}