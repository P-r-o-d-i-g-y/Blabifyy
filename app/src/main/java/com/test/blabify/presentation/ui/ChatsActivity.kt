package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import android.widget.PopupMenu
import com.test.blabify.R
import com.test.blabify.domain.models.ChatItem
import com.test.blabify.presentation.adapters.ChatAdapter

class ChatsActivity : AppCompatActivity() {
    private var selectedColor: String = "all"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<ChatItem>()
    private lateinit var originalChatList: List<ChatItem>
    private lateinit var drawerLayout: DrawerLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.recycler_view_chats)
        recyclerView.layoutManager = LinearLayoutManager(this)

        drawerLayout = findViewById(R.id.drawer_layout)






        // Наполнение списка
        chatList.add(ChatItem("Название 1", "Вы: отредактировать...", R.drawable.chat_av,"blue"))
        chatList.add(ChatItem("Название 2", "Вы: файл", R.drawable.chat_av, "yellow"))
        chatList.add(ChatItem("Название 3", "Игрок: выполнил ред...", R.drawable.chat_av, "blue"))
        chatList.add(ChatItem("Название 4", "Вы: сообщений нет", R.drawable.chat_av, "black"))
        chatList.add(ChatItem("Название 5", "Вы: сообщений нет", R.drawable.chat_av, "blue"))
        chatList.add(ChatItem("Название 6", "Вы: привет...", R.drawable.chat_av, "yellow"))

        originalChatList = chatList.toList()  // Сохраняем полный список
        adapter = ChatAdapter(chatList) { chatItem ->
            val intent = Intent(this, Chat::class.java)
            // можно добавить: intent.putExtra("chatTitle", chatItem.title)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        // Устанавливаем обработчик нажатия на mark_icon
        val markIcon: ImageView = findViewById(R.id.mark_icon)
        markIcon.setOnClickListener { view ->
            showColorPopup(view)
        }

    }

    private fun showColorPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.color_select_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            selectedColor = when (item.itemId) {
                R.id.mark_black -> "black"
                R.id.mark_blue -> "blue"
                R.id.mark_yellow -> "yellow"
                R.id.reset_filter -> "all"  // Сбросить фильтрацию
                else -> "all"
            }
            updateMarkIcon(selectedColor)
            filterChats()
            true
        }
        popup.show()
    }
    // Функция для изменения иконки "Закладки" в Toolbar
    private fun updateMarkIcon(color: String) {
        val markIcon: ImageView = findViewById(R.id.mark_icon)
        val iconRes = when (color) {
            "black" -> R.drawable.mark_black
            "blue" -> R.drawable.mark_blue
            "yellow" -> R.drawable.mark_yellow
            else -> R.drawable.mark_search  // Иконка по умолчанию (сброс фильтрации)
        }
        markIcon.setImageResource(iconRes)
    }

    // Функция для фильтрации чатов по цвету
    private fun filterChats() {
        val filteredList = if (selectedColor == "all") {
            originalChatList  // Возвращаем полный список
        } else {
            originalChatList.filter { it.mark == selectedColor }  // Фильтрация по оригинальному списку
        }
        chatList.clear()  // Очищаем текущий список
        chatList.addAll(filteredList)  // Добавляем отфильтрованные данные
        adapter.updateList(filteredList)  // Обновляем список в адаптере
    }
}