package com.test.blabify.presentation.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.models.Folder

class FolderAdapter(
    private val folders: List<Folder>,
    private val onClick: (Folder) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageButton = view.findViewById(R.id.folder_button)
        val name: TextView = view.findViewById(R.id.folder_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.folder_item, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.name.text = folder.name
        holder.icon.setOnClickListener { onClick(folder) } // Слушатель на ImageButton
    }

    override fun getItemCount() = folders.size
}