package com.test.blabify.presentation.ui

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.test.blabify.R
import com.test.blabify.data.FirebaseUtil
import com.test.blabify.domain.models.Message

class Chat : AppCompatActivity() {
    private lateinit var messageInput: EditText
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
        val userName = intent.getStringExtra("userName") ?: "Аноним"
        sendMessageBtn.setOnClickListener { v ->
            Log.d("Chat", "Нажали на кнопку отправки")
            val message: String = messageInput.getText().toString().trim()
            if (message.isEmpty()) return@setOnClickListener
            sendMessageToUser(chatroomId, userName, message)
        }
    }
    fun sendMessageToUser(chatroomId: String, userName: String, messageText: String) {
        val message = Message(
            userName = userName,
            textMessage = messageText,
            messageTime = System.currentTimeMillis()
        )
        Log.d("sendMessageToUser", "Попытка отправки сообщения: $message")
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
}