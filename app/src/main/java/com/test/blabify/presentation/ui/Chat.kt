package com.test.blabify.presentation.ui

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.test.blabify.R
import com.test.blabify.data.FirebaseUtil
import com.test.blabify.domain.models.Message
import com.test.blabify.presentation.adapters.MessageAdapter

class Chat : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            finish() // Закрывает Chat и возвращает на предыдущую активити
        }
        messageInput = findViewById(R.id.message_input)
        val sendMessageBtn = findViewById<ImageButton>(R.id.send_mes)
        val chatroomId = intent.getStringExtra("chatroomId") ?: return
        Log.d("Chat", "chatroomId = $chatroomId")
        setupChatRecyclerView(chatroomId)
        val userName = intent.getStringExtra("userName") ?: "Аноним"
        sendMessageBtn.setOnClickListener { v ->
            Log.d("Chat", "Нажали на кнопку отправки")
            val message: String = messageInput.getText().toString().trim()
            if (message.isEmpty()) return@setOnClickListener
            sendMessageToUser(chatroomId, userName, message)
        }
    }
    fun sendMessageToUser(chatroomId: String, userName: String, messageText: String) {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val message = Message(
            userName = userName,
            senderId = senderId,
            textMessage = messageText,
            messageTime = System.currentTimeMillis()
        )
        FirebaseUtil.getMessagesRef(chatroomId)
            .push()
            .setValue(message)
            .addOnSuccessListener {
                Log.d("sendMessageToUser", "Сообщение успешно отправлено")
                messageInput.setText("")
            }
            .addOnFailureListener { error ->
                Log.e("sendMessageToUser", "Ошибка при отправке сообщения: ${error.message}")
            }
    }
    private fun setupChatRecyclerView(chatroomId: String) {
        recyclerView = findViewById(R.id.recycler_view_messages)

        val manager = LinearLayoutManager(this)
        manager.stackFromEnd = true
        recyclerView.layoutManager = manager

        adapter = MessageAdapter(this, messages)
        recyclerView.adapter = adapter

        FirebaseUtil.getMessagesRef(chatroomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    messages.clear()
                    for (child in snapshot.children) {
                        val message = child.getValue(Message::class.java)
                        if (message != null) messages.add(message)
                    }
                    adapter.notifyDataSetChanged()
                    recyclerView.smoothScrollToPosition(messages.size)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Chat", "Ошибка чтения сообщений: ${error.message}")
                }
            })
    }
}