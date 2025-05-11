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
    val folders: List<Folder>,
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
        // Меняем иконку в зависимости от состояния папки
        if (folder.isOpened) {
            holder.icon.setImageResource(R.drawable.folder_open2)  // Иконка для открытой папки
        } else {
            holder.icon.setImageResource(R.drawable.folder)  // Иконка для закрытой папки
        }

        // Обработчик нажатия на папку
        holder.icon.setOnClickListener {
            // Закрываем все другие папки, кроме текущей
            for (i in folders.indices) {
                if (i != position) {
                    folders[i].isOpened = false // Закрываем все другие папки
                }
            }

            // Переключаем состояние текущей папки
            folder.isOpened = !folder.isOpened

            // Обновляем адаптер, чтобы применить изменения
            notifyDataSetChanged()  // Обновляем все элементы списка

            onClick(folder)  // Вызов функции обработки клика (если нужно)
        }
    }

    override fun getItemCount() = folders.size
}