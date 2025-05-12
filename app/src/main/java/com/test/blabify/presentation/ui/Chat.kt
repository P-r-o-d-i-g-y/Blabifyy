package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.test.blabify.R
import com.test.blabify.data.FirebaseUtil
import com.test.blabify.data.uploadFileToSupabase
import com.test.blabify.domain.models.AttachmentType
import com.test.blabify.domain.models.Message
import com.test.blabify.presentation.adapters.MessageAdapter
import kotlinx.coroutines.launch
import java.util.UUID


class Chat : AppCompatActivity() {
    companion object {
        const val FILE_PICK_CODE = 1001
    }
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
        val attachButton = findViewById<ImageButton>(R.id.btn_attach)
        attachButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(Intent.createChooser(intent, "Выберите файл"), FILE_PICK_CODE)
        }
    }
    @Suppress("DEPRECATION")
    @Deprecated("onActivityResult is deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("FileUpload", "onActivityResult: FILE_PICK_CODE matched and result OK")
        if (requestCode == FILE_PICK_CODE && resultCode == RESULT_OK && data != null && data.data != null) {
            val fileUri = data.data
            Log.d("FileUpload", "Selected fileUri: $fileUri")
            if (fileUri != null) {
                lifecycleScope.launch {
                    val chatRoomId = intent.getStringExtra("chatroomId") ?: return@launch
                    val userName = intent.getStringExtra("userName") ?: "Аноним"
                    val fileName = fileUri.lastPathSegment ?: "file"
                    val inputStream = contentResolver.openInputStream(fileUri)
                    val fileSize = inputStream?.available()?.toLong() ?: 0L
                    Log.d("FileUpload", "InputStream available = $fileSize bytes")

                    val fileUrl = uploadFileToSupabase(this@Chat, fileUri, chatRoomId)
                    Log.d("FileUpload", "Uploaded fileUrl: $fileUrl")
                    if (fileUrl != null) {
                        val type = contentResolver.getType(fileUri) ?: ""
                        val attachmentType = when {
                            type.startsWith("image/") -> AttachmentType.IMAGE
                            type == "application/pdf" -> AttachmentType.PDF
                            type.contains("sheet") || type.contains("excel") -> AttachmentType.TABLE
                            else -> AttachmentType.FILE
                        }

                        sendMessageToUser(
                            chatRoomId,
                            userName,
                            messageText = null,
                            attachmentUrl = fileUrl,
                            attachmentType = attachmentType,
                            fileName = fileName,
                            fileSize = fileSize
                        )

                        saveFileMetaToFirestore(
                            chatRoomId,
                            fileName,
                            fileUrl,
                            fileSize,
                            attachmentType
                        )
                    } else {
                        Log.e("FileUpload", "Ошибка: uploadFileToSupabase вернул null")
                        return@launch
                    }
                }
            }
        }
    }

    fun sendMessageToUser(chatroomId: String, userName: String, messageText: String? = null, attachmentUrl: String? = null, attachmentType: AttachmentType? = null, fileName: String? = null, fileSize: Long? = null) {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val message = Message(
            userName = userName,
            senderId = senderId,
            textMessage = messageText,
            messageTime = System.currentTimeMillis(),
            attachmentUrl = attachmentUrl,
            attachmentType = attachmentType,
            fileName = fileName,
            fileSize = fileSize
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
            }
        )
    }
    private fun saveFileMetaToFirestore(
        chatroomId: String,
        fileName: String,
        fileUrl: String,
        fileSize: Long,
        attachmentType: AttachmentType
    ) {
        Log.d("Firestore", "Ищем папку с chatId = $chatroomId")
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("folders")
            .whereEqualTo("chatId", chatroomId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val folder = result.documents[0]
                    val folderId = folder.id

                    val fileMeta = mapOf(
                        "name" to fileName,
                        "size" to fileSize,
                        "url" to fileUrl,
                        "type" to attachmentType.toString(),
                        "uploaded_at" to System.currentTimeMillis(),
                        "createdBy" to FirebaseAuth.getInstance().currentUser?.uid
                    )

                    val newFileId = UUID.randomUUID().toString()
                    firestore.collection("folders")
                        .document(folderId)
                        .collection("files")
                        .document(newFileId) // теперь явно указываешь ID
                        .set(fileMeta)
                        .addOnSuccessListener {
                            Log.d("Firestore", "Метаданные файла успешно сохранены для folderId = $folderId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Ошибка сохранения метаданных файла: ${e.message}")
                        }
                }
            }
    }
}