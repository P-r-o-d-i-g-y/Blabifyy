package com.test.blabify.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.usecases.ResortSuggestionV2

class ResortSuggestionAdapter(
    private val items: List<ResortSuggestionV2>,
    private val onSelectionChanged: (selectedCount: Int, totalCount: Int) -> Unit
) : RecyclerView.Adapter<ResortSuggestionAdapter.VH>() {

    private val selectedFileIds = items.map { it.fileId }.toMutableSet()

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cbSelected: CheckBox = v.findViewById(R.id.cbSelected)
        val tvFile: TextView = v.findViewById(R.id.tvFile)
        val tvFromTo: TextView = v.findViewById(R.id.tvFromTo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_resort_suggestion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.tvFile.text = item.fileName ?: "(без имени)"
        h.tvFromTo.text = "${item.fromPath}  →  ${item.toPath}"

        h.cbSelected.setOnCheckedChangeListener(null)
        h.cbSelected.isChecked = selectedFileIds.contains(item.fileId)

        h.cbSelected.setOnCheckedChangeListener { _, isChecked ->
            setSelected(item, isChecked)
        }
        h.itemView.setOnClickListener {
            val newChecked = !selectedFileIds.contains(item.fileId)
            h.cbSelected.isChecked = newChecked
            setSelected(item, newChecked)
        }
    }

    override fun getItemCount(): Int = items.size

    fun getSelectedItems(): List<ResortSuggestionV2> {
        return items.filter { selectedFileIds.contains(it.fileId) }
    }

    fun selectAll() {
        selectedFileIds.clear()
        selectedFileIds += items.map { it.fileId }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun clearSelection() {
        selectedFileIds.clear()
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    fun areAllSelected(): Boolean {
        return items.isNotEmpty() && selectedFileIds.size == items.size
    }

    private fun setSelected(item: ResortSuggestionV2, selected: Boolean) {
        if (selected) {
            selectedFileIds += item.fileId
        } else {
            selectedFileIds -= item.fileId
        }

        notifySelectionChanged()
    }

    private fun notifySelectionChanged() {
        onSelectionChanged(selectedFileIds.size, items.size)
    }
}