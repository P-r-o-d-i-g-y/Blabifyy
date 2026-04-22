package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.test.blabify.R
import com.test.blabify.data.FirebaseUtil
import com.test.blabify.domain.models.ChatRoom
import com.test.blabify.presentation.adapters.ChatAdapter
import java.util.UUID
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.test.blabify.domain.models.Folder
//для кастомного меню выездного
import com.test.blabify.presentation.ui.widgets.ColorPopup
import com.test.blabify.presentation.adapters.ColorMenuItem



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

        val treeButton = findViewById<ImageButton>(R.id.btn_tree)
        treeButton.setOnClickListener {
            val intent = Intent(this, Folders::class.java)
            startActivity(intent)
        }

        originalChatList = chatList.toList()  // Сохраняем полный список
        adapter = ChatAdapter(chatList) { chatRoom ->
            val intent = Intent(this, Chat::class.java)
            intent.putExtra("chatroomId", chatRoom.chatId)  // <--- обязателен
            FirebaseUtil.getCurrentUserName { userName ->
                intent.putExtra("userName", userName)
                startActivity(intent)
            }
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
            showChatNameDialog()
        }

    }

    private fun showColorPopup(anchor: View) {
        val items = listOf(
            ColorMenuItem(R.id.mark_black,  "Black",  R.drawable.mark_black,  section = 0),
            ColorMenuItem(R.id.mark_blue,   "Blue",   R.drawable.mark_blue,   section = 0),
            ColorMenuItem(R.id.mark_yellow, "Yellow", R.drawable.mark_yellow, section = 0),

            // секция действий
            ColorMenuItem(R.id.reset_filter, "Reset", null, section = 1)
        )

        val popup = ColorPopup(
            context = this,
            items = items,
            onItemClick = { item ->
                selectedColor = when (item.id) {
                    R.id.mark_black -> "black"
                    R.id.mark_blue -> "blue"
                    R.id.mark_yellow -> "yellow"
                    R.id.reset_filter -> "all"
                    else -> "all"
                }
                updateMarkIcon(selectedColor)
                filterChats()
            },
            isSectionBreak = { pos ->
                // рисуем белую «пилюлю» ПЕРЕД первым элементом новой секции
                pos in 1..items.lastIndex && (items[pos - 1].section != items[pos].section)
            }
        )
        popup.show(anchor)
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
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val dbRef = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")
            .getReference("chatRooms")

        val query = dbRef.orderByChild("ownerId").equalTo(uid)

        query.get().addOnSuccessListener { snapshot ->
            chatList.clear()
            for (chatSnap in snapshot.children) {
                val chatRoom = chatSnap.getValue(ChatRoom::class.java)
                if (chatRoom != null) {
                    chatList.add(chatRoom)
                }
            }
            originalChatList = chatList.toList()
            adapter.updateList(chatList)
        }.addOnFailureListener { e ->
            Log.e("FIREBASE_DEBUG", "Error loading chats: ${e.message}", e)
            Toast.makeText(this, "Ошибка загрузки чатов: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun createNewChatRoom(chatName: String) {
        val database = FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")
        val chatRoomsRef = database.getReference("chatRooms")

        val chatId = chatRoomsRef.push().key ?: run {
            Log.e("CHAT_DEBUG", "push().key returned null")
            return
        } // генерируем уникальный ID
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"

        Log.d("CHAT_DEBUG", "START createNewChatRoom chatId=$chatId uid=$uid chatName=$chatName")

        val newChatRoom = ChatRoom(
            chatId = chatId,
            title = chatName,
            ownerId = uid,
            participantIds = listOf(uid)
        )

        val completeChatRoom = newChatRoom.copy(
            subtitle = "Вы: сообщений нет",
            iconResId = R.drawable.chat_av,
            mark = ""
        )

        Log.d("CHAT_DEBUG", "BEFORE RTDB setValue path=chatRooms/$chatId value=$completeChatRoom")

        chatRoomsRef.child(chatId).setValue(completeChatRoom).addOnSuccessListener {
            Log.d("CHAT_DEBUG", "RTDB SUCCESS chatId=$chatId")
            chatList.add(completeChatRoom)
            adapter.updateList(chatList)
            Log.d("CHAT_DEBUG", "CALL createAssociatedFolder chatId=$chatId")
            createAssociatedFolder(chatId, chatName, uid)
        }.addOnFailureListener { e ->
            Log.e("CHAT_DEBUG", "RTDB FAILURE chatId=$chatId message=${e.message}", e)
            Toast.makeText(this, "Ошибка создания чата: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun createAssociatedFolder(chatId: String, name: String, userId: String) {
        Log.d("FIRESTORE_DEBUG", "Attempting to write folder to Firestore")

        val folderId = UUID.randomUUID().toString()

        val newFolderData = mapOf(
            "id" to folderId,
            "name" to name,
            "parentId" to null,
            "chatId" to chatId,
            "createdBy" to userId,
            "createdAt" to System.currentTimeMillis(),
            "rootFolderId" to folderId      //  ключевая строчка
        )

        //Log.d("FIRESTORE_DEBUG", "User ID: ${userId}, Folder Name: ${name}")

        FirebaseFirestore.getInstance()
            .collection("folders")
            .document(folderId)
            .set(newFolderData)
            .addOnSuccessListener {
                Log.d("FIRESTORE_DEBUG", "Folder successfully written to Firestore")
                Toast.makeText(this, "Папка создана", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Log.e("FIRESTORE_DEBUG", "Error writing folder to Firestore: ${it.message}", it)
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
    }
    private fun showChatNameDialog() {
        val input = EditText(this)
        input.hint = "Введите название чата"

        AlertDialog.Builder(this)
            .setTitle("Новый чат")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val chatName = input.text.toString().trim()
                if (chatName.isNotEmpty()) {
                    createNewChatRoom(chatName)
                } else {
                    Toast.makeText(this, "Название чата не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

}