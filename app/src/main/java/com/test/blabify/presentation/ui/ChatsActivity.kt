package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.PopupMenu
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.test.blabify.R
import com.test.blabify.domain.models.ChatRoom
import com.test.blabify.presentation.adapters.ChatAdapter

class ChatsActivity : AppCompatActivity() {
    private var selectedColor: String = "all"

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val chatList = mutableListOf<ChatRoom>()
    private lateinit var originalChatList: List<ChatRoom>
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
        //chatList.add(ChatItem("Название 1", "Вы: отредактировать...", R.drawable.chat_av,"blue"))
        //chatList.add(ChatItem("Название 2", "Вы: файл", R.drawable.chat_av, "yellow"))
        //chatList.add(ChatItem("Название 3", "Игрок: выполнил ред...", R.drawable.chat_av, "blue"))
        //chatList.add(ChatItem("Название 4", "Вы: сообщений нет", R.drawable.chat_av, "black"))
        //chatList.add(ChatItem("Название 5", "Вы: сообщений нет", R.drawable.chat_av, "blue"))
        //chatList.add(ChatItem("Название 6", "Вы: привет...", R.drawable.chat_av, "yellow"))

        originalChatList = chatList.toList()  // Сохраняем полный список
        adapter = ChatAdapter(chatList) { ChatRoom ->
            val intent = Intent(this, Chat::class.java)
            // можно добавить: intent.putExtra("chatTitle", chatItem.title)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        loadChatRoomsFromFirebase()
        // Устанавливаем обработчик нажатия на mark_icon
        val markIcon: ImageView = findViewById(R.id.mark_icon)
        markIcon.setOnClickListener { view ->
            showColorPopup(view)
        }

        val createButton = findViewById<ImageButton>(R.id.crate_button)
        createButton.setOnClickListener {
            createNewChatRoom()
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
    private fun loadChatRoomsFromFirebase() {
        val dbRef = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")
            .getReference("chatRooms")

        dbRef.get().addOnSuccessListener { snapshot ->
            chatList.clear()
            for (chatSnap in snapshot.children) {
                val chatRoom = chatSnap.getValue(ChatRoom::class.java)
                if (chatRoom != null) {
                    chatList.add(chatRoom)
                }
            }
            originalChatList = chatList.toList()
            adapter.updateList(chatList)
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка загрузки чатов", Toast.LENGTH_SHORT).show()
        }
    }
    private fun createNewChatRoom() {
        val database = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")
        val chatRoomsRef = database.getReference("chatRooms")

        val chatId = chatRoomsRef.push().key ?: return // генерируем уникальный ID
        val newChatRoom = ChatRoom(
            chatId = chatId,
            title = "чат1",
            participantIds = listOf(FirebaseAuth.getInstance().currentUser?.uid ?: "unknown")
        )

        val completeChatRoom = newChatRoom.copy(
            subtitle = "Вы: сообщений нет",
            iconResId = R.drawable.chat_av,
            mark = ""
        )

        chatRoomsRef.child(chatId).setValue(completeChatRoom).addOnSuccessListener {
            chatList.add(completeChatRoom)
            adapter.updateList(chatList)
        }.addOnFailureListener {
            Toast.makeText(this, "Ошибка создания чата: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
}