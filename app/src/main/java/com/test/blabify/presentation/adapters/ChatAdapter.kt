package com.test.blabify.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.test.blabify.R
import com.test.blabify.domain.models.ChatRoom

class ChatAdapter(
    private val chatList: List<ChatRoom>,
    private val onItemClick: (ChatRoom) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var filteredChatList: List<ChatRoom> = chatList

    fun updateList(newList: List<ChatRoom>) {
        filteredChatList = newList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return filteredChatList.size
    }


    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val chatIcon: ImageView = itemView.findViewById(R.id.chat_icon)
        val chatTitle: TextView = itemView.findViewById(R.id.chat_title)
        val chatSubtitle: TextView = itemView.findViewById(R.id.chat_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chatItem = chatList[position]
        holder.chatTitle.text = chatItem.title
        holder.chatSubtitle.text = chatItem.subtitle
        holder.chatIcon.setImageResource(chatItem.iconResId)

        // Устанавливаем правильную иконку закладки в зависимости от цвета
        val markIconRes = when (chatItem.mark) {
            "black" -> R.drawable.mark_black
            "blue" -> R.drawable.mark_blue
            "yellow" -> R.drawable.mark_yellow
            else -> R.drawable.mark
        }
        holder.itemView.findViewById<ImageView>(R.id.bookmark_icon).setImageResource(markIconRes)

        // ⬇ Обработчик нажатия
        holder.itemView.setOnClickListener {
            onItemClick(chatItem)
        }
    }

}