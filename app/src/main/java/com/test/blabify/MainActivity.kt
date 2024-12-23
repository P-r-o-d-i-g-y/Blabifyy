package com.test.blabify

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

class MainActivity : AppCompatActivity() {
    private var selectedColor: String = "all"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<ChatItem>()
    private lateinit var originalChatList: List<ChatItem>
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var smallNavView: NavigationView
    private lateinit var fullNavView: NavigationView

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
        smallNavView = findViewById(R.id.small_nav_view)
        fullNavView = findViewById(R.id.full_nav_view)


        // Открытие полного меню при нажатии на узкое меню
        smallNavView.setNavigationItemSelectedListener {
            toggleNavigationMenu()
            true
        }

        // Закрытие полного меню при нажатии на пункт
        fullNavView.setNavigationItemSelectedListener {
            toggleNavigationMenu()
            true
        }




        // Наполнение списка
        chatList.add(ChatItem("Название 1", "Вы: отредактировать...", R.drawable.chat_av,"blue"))
        chatList.add(ChatItem("Название 2", "Вы: файл", R.drawable.chat_av, "yellow"))
        chatList.add(ChatItem("Название 3", "Игрок: выполнил ред...", R.drawable.chat_av, "blue"))
        chatList.add(ChatItem("Название 4", "Вы: сообщений нет", R.drawable.chat_av, "black"))
        chatList.add(ChatItem("Название 5", "Вы: сообщений нет", R.drawable.chat_av, "blue"))
        chatList.add(ChatItem("Название 6", "Вы: привет...", R.drawable.chat_av, "yellow"))

        originalChatList = chatList.toList()  // Сохраняем полный список
        adapter = ChatAdapter(chatList)
        recyclerView.adapter = adapter
        // Устанавливаем обработчик нажатия на mark_icon
        val markIcon: ImageView = findViewById(R.id.mark_icon)
        markIcon.setOnClickListener { view ->
            showColorPopup(view)
        }

    }
    // Логика переключения меню
    private fun toggleNavigationMenu() {
        if (smallNavView.visibility == NavigationView.VISIBLE) {
            smallNavView.visibility = NavigationView.GONE
            fullNavView.visibility = NavigationView.VISIBLE
            drawerLayout.openDrawer(fullNavView)
        } else {
            fullNavView.visibility = NavigationView.GONE
            smallNavView.visibility = NavigationView.VISIBLE
            drawerLayout.closeDrawer(fullNavView)
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