package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.test.blabify.data.supabase.uploadFileToSupabase
import com.test.blabify.domain.models.AttachmentType
import com.test.blabify.domain.models.Message
import com.test.blabify.presentation.adapters.MessageAdapter
import kotlinx.coroutines.launch
import java.util.UUID
import com.test.blabify.data.repositories.FirestoreRepositoryImpl
import com.test.blabify.presentation.ui.widgets.ColorPopup
import com.test.blabify.presentation.adapters.ColorMenuItem
import kotlinx.coroutines.tasks.await



class Chat : AppCompatActivity() {
    companion object {
        const val FILE_PICK_CODE = 1001
    }
    private lateinit var messageInput: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var pendingResortSuggestionsV2: List<com.test.blabify.domain.usecases.ResortSuggestionV2> = emptyList()
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
        supportFragmentManager.setFragmentResultListener(
            ResortBottomSheet.RESULT_KEY,
            this
        ) { _, bundle ->
            val accepted = bundle.getBoolean(ResortBottomSheet.RESULT_ACCEPTED, false)

            if (!accepted) {
                pendingResortSuggestionsV2 = emptyList()
                return@setFragmentResultListener
            }

            // Пока заглушка на применение предложений второго контура
            android.widget.Toast.makeText(
                this,
                "Ок, применить (заглушка): ${pendingResortSuggestionsV2.size}",
                android.widget.Toast.LENGTH_SHORT
            ).show()

            pendingResortSuggestionsV2 = emptyList()
        }
        messageInput = findViewById(R.id.message_input)
        val sendMessageBtn = findViewById<ImageButton>(R.id.send_mes)
        val chatroomId = intent.getStringExtra("chatroomId") ?: return
        Log.d("Chat", "chatroomId = $chatroomId")
        setupChatRecyclerView(chatroomId)
        val userName = intent.getStringExtra("userName") ?: "Аноним"
        sendMessageBtn.setOnClickListener { v ->
            Log.d("Chat", "Clicked the send button")
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
        val btnSettings = findViewById<ImageButton>(R.id.btn_settings)
        btnSettings.setOnClickListener { v -> showSettingsPopup(v) }

    }
    /**
     * Возвращает ID корневой папки (root) для текущего чата.
     *
     * Зачем: при клике "Пересортировка ветки" нам нужно знать, в какой корневой папке (ветке=чате)
     * считать кандидатов/файлы. У каждой записи-папки в Firestore у корня `parentId == null`
     * и `chatId == <id чата>`. Мы находим ровно один такой документ и берём его id.
     *
     * suspend: Firestore-запрос асинхронный. Функцию вызываем из корутины (lifecycleScope.launch { ... }).
     *
     * @param chatId  ID чата (мы его кладём в Intent при открытии Chat)
     * @return id корневой папки или null, если ничего не нашли
     */
    private suspend fun getRootFolderIdForChat(chatId: String): String? {
        // Берём инстанс Firestore
        val fs = FirebaseFirestore.getInstance()
        // Делаем запрос в коллекцию "folders":
        // — chatId совпадает с нужным чатом
        // — parentId == null, значит это именно корневая папка чата
        // — limit(1), потому что ожидаем максимум одну запись
        val snap = fs.collection("folders")
            .whereEqualTo("chatId", chatId)
            .whereEqualTo("parentId", null)
            .limit(1)
            .get()
            .await() // ждём результат внутри корутины
        // Если документ найден — вернём его id, иначе null
        return snap.documents.firstOrNull()?.id
    }


    @Suppress("DEPRECATION")
    @Deprecated("onActivityResult is deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_PICK_CODE && resultCode == RESULT_OK && data != null && data.data != null) {
            val fileUri = data.data
            Log.d("FileUpload", "Selected fileUri: $fileUri")
            if (fileUri != null) {
                lifecycleScope.launch {
                    val chatRoomId = intent.getStringExtra("chatroomId") ?: return@launch
                    val userName = intent.getStringExtra("userName") ?: "Аноним"
                    val fileName = fileUri.lastPathSegment ?: "file"
                    val fileSize = getFileSize(fileUri)
                    Log.d("FileUpload", "Resolved fileSize = $fileSize bytes")
                    Log.d("FileUpload", "onActivityResult: FILE_PICK_CODE matched and result OK")
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
                            attachmentType,
                            fileUri
                        )
                    } else {
                        Log.e("FileUpload", "Error: uploadFileToSupabase returned null")
                        return@launch
                    }
                }
            }
        }
    }
    private fun getFileSize(uri: android.net.Uri): Long {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getLong(sizeIndex)
            }
        }

        return contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            afd.length
        } ?: 0L
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
                Log.d("sendMessageToUser", "Message sent successfully")
                messageInput.setText("")
            }
            .addOnFailureListener { error ->
                Log.e("sendMessageToUser", "Error sending message: ${error.message}")
            }
    }
    private fun setupChatRecyclerView(chatroomId: String) {
        recyclerView = findViewById(R.id.recycler_view_messages)

        val manager = LinearLayoutManager(this)
        manager.stackFromEnd = true
        recyclerView.layoutManager = manager

        adapter = MessageAdapter(
            context = this,
            messages = messages,
            onFileClick = { msg ->
                // открыть/скачать файл по msg.attachmentUrl
                // например, стартуем ACTION_VIEW с Uri.parse(msg.attachmentUrl)
            },
            onDetailsClick = { msg ->
                // показать нижний шит с деталями: имя, размер, тип, время, пользователь
            }
        )
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
                    Log.e("Chat", "Error reading messages: ${error.message}")
                }
            }
        )
    }
    private fun saveFileMetaToFirestore(
        chatroomId: String,
        fileName: String,
        fileUrl: String,
        fileSize: Long,
        attachmentType: AttachmentType,
        fileUri: android.net.Uri
    ) {
        Log.d("Firestore", "We are looking for a folder with chatId = $chatroomId")
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("folders")
            .whereEqualTo("chatId", chatroomId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val folder = result.documents[0]
                    val folderId = folder.id

                    // 🔽 генерируем id сразу
                    val newFileId = UUID.randomUUID().toString()

                    // 🔽 ДОБАВЬ ЭТО (перед созданием модели)
                    val exif: Map<String, Any?> = if (attachmentType == AttachmentType.IMAGE) {
                        extractImageMeta(fileUri) // <<— тебе доступен fileUri из onActivityResult
                    } else emptyMap()

                    //  создаём объект FileMeta
                    val fileMetaObj = com.test.blabify.domain.models.FileMeta(
                        id = newFileId,
                        name = fileName,
                        size = fileSize,
                        url = fileUrl,
                        type = attachmentType.toString(),
                        uploadedAt = System.currentTimeMillis(),
                        createdBy = FirebaseAuth.getInstance().currentUser?.uid,
                        pinned = false,
                        confidence = null,
                        movedAt = null,

                        // НОВОЕ: сразу создаём поля для «липкости к ветке»
                        classificationBranch = null,
                        classificationFolderId = null,
                        classificationScore = null,

                        exifDate = exif["exifDate"] as String?,
                        exifCamera = exif["exifCamera"] as String?,
                        exifLat = (exif["exifLat"] as? Double),
                        exifLng = (exif["exifLng"] as? Double),

                        // для ускорения
                        chatId = chatroomId,
                        rootFolderId = folderId       // корень ветки
                    )

                    firestore.collection("folders")
                        .document(folderId)
                        .collection("files")
                        .document(newFileId) // теперь явно указываешь ID
                        .set(fileMetaObj) // <-- вот тут кладём модель
                        .addOnSuccessListener {
                            Log.d(
                                "Firestore",
                                "File metadata saved successfully for folderId = $folderId"
                            )
                            // Запуск автосортировки
                            lifecycleScope.launch {
                                val repo = FirestoreRepositoryImpl()

                                // 1) Все потомки корневой папки чата
                                val descendants = repo.getDescendantFolders(folderId)

                                // 2) Индекс детей по parentId
                                /*val childrenByParent = descendants.groupBy { it.parentId }

                                // 3) DFS: строим кандидатов с level и path
                                val candidates = mutableListOf<com.test.blabify.domain.api.CandidateFolder>()

                                fun walk(parentId: String, level: Int, path: String) {
                                    val kids = childrenByParent[parentId].orEmpty()
                                    for (f in kids) {
                                        val childPath = if (path.isEmpty()) f.name else "$path/${f.name}"
                                        candidates += com.test.blabify.domain.api.CandidateFolder(
                                            id = f.id,
                                            name = f.name,
                                            level = level,
                                            path = childPath
                                        )
                                        walk(f.id, level + 1, childPath)
                                    }
                                }
                                walk(folderId, 0, "") // корень не добавляем — только его подпапки

                                // 4) Организатор по новому контракту (с веткой и score)
                                val organizer = com.test.blabify.domain.usecases.FileAutoOrganizer(
                                    getFolders = { candidates },
                                    getFiles = { repo.getFilesInFolder(folderId) },
                                    downloadText = { url -> com.test.blabify.data.supabase.SupabaseTextDownloader.downloadTextFromUrl(url) },
                                    moveFile = { fileId, targetId, branch, score ->
                                        repo.updateFileFolder(
                                            fileId = fileId,
                                            parentId = folderId,
                                            childId = targetId,
                                            classificationBranch = branch,
                                            classificationScore = score
                                        )
                                    },
                                    classifier = com.test.blabify.domain.impl.RuleBasedClassifier()
                                )
                                organizer.organize()*/
                                // 4) Новый координатор: после появления файла всегда выполняем 1-й контур
                                val candidates = buildCandidates(folderId, descendants)

                                val coordinator = buildCoordinator(
                                    rootFolderId = folderId,
                                    candidates = candidates,
                                    repo = repo
                                )

                                coordinator.runPrimaryPlacement()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "Error saving file: ${e.message}")
                        }
                }
            }
    }
    private fun extractImageMeta(uri: android.net.Uri): Map<String, Any?> {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = androidx.exifinterface.media.ExifInterface(input)

                val date =
                    exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)

                val latLong = FloatArray(2)
                val hasLatLng = exif.getLatLong(latLong)
                val make = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)
                val model = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)

                mapOf(
                    "exifDate" to date,
                    "exifCamera" to listOfNotNull(make, model).joinToString(" ").ifBlank { null },
                    "exifLat" to if (hasLatLng) latLong[0].toDouble() else null,
                    "exifLng" to if (hasLatLng) latLong[1].toDouble() else null
                )
            } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
    private fun showSettingsPopup(anchor: View) {
        val items = listOf(
            ColorMenuItem(R.id.action_resort_branch, "Пересортировка ветки"),
            ColorMenuItem(R.id.action_mark_color,   "Цвет закладки")
        )

        ColorPopup(
            context = this,
            items = items,
            onItemClick = { item ->
                when (item.id) {
                    R.id.action_resort_branch -> {
                        lifecycleScope.launch {
                            val chatId = intent.getStringExtra("chatroomId") ?: return@launch

                            // 1) найти корневую папку этого чата
                            val rootId = getRootFolderIdForChat(chatId)
                            if (rootId == null) {
                                android.widget.Toast.makeText(this@Chat, "Не найден корень чата", android.widget.Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            // 2) построить предложения
                            val repo = com.test.blabify.data.repositories.FirestoreRepositoryImpl()

                            val descendants = repo.getDescendantFolders(rootId)
                            val candidates = buildCandidates(rootId, descendants)

                            val coordinator = buildCoordinator(
                                rootFolderId = rootId,
                                candidates = candidates,
                                repo = repo
                            )

                            val suggestionsV2 = coordinator.buildResortSuggestions()

                            if (suggestionsV2.isEmpty()) {
                                android.widget.Toast.makeText(
                                    this@Chat,
                                    "Пока нечего пересортировать",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                pendingResortSuggestionsV2 = suggestionsV2

                                val uiSuggestions = ArrayList(
                                    suggestionsV2.map {
                                        com.test.blabify.domain.usecases.ResortSuggestion(
                                            fileId = it.fileId,
                                            fileName = it.fileName,
                                            fromPath = it.fromPath,
                                            toPath = it.toPath
                                        )
                                    }
                                )

                                val sheet = com.test.blabify.presentation.ui.ResortBottomSheet
                                    .newInstance(uiSuggestions)

                                sheet.show(supportFragmentManager, "resortSheet")
                            }
                        }
                    }
                    R.id.action_mark_color -> {
                        android.widget.Toast.makeText(this, "Цвет закладки", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            // белая "пилюля" между двумя пунктами
            isSectionBreak = { pos -> pos == 1 }
        ).show(anchor)
    }
    private fun buildCandidates(
        rootFolderId: String,
        descendants: List<com.test.blabify.domain.models.Folder>
    ): List<com.test.blabify.domain.api.CandidateFolder> {
        val childrenByParent = descendants.groupBy { it.parentId }
        val candidates = mutableListOf<com.test.blabify.domain.api.CandidateFolder>()

        fun walk(parentId: String, level: Int, path: String) {
            val kids = childrenByParent[parentId].orEmpty()
            for (f in kids) {
                val childPath = if (path.isEmpty()) f.name else "$path/${f.name}"
                candidates += com.test.blabify.domain.api.CandidateFolder(
                    id = f.id,
                    name = f.name,
                    level = level,
                    path = childPath
                )
                walk(f.id, level + 1, childPath)
            }
        }

        walk(rootFolderId, 0, "")
        return candidates
    }
    private fun buildCoordinator(
        rootFolderId: String,
        candidates: List<com.test.blabify.domain.api.CandidateFolder>,
        repo: com.test.blabify.data.repositories.FirestoreRepositoryImpl
    ): com.test.blabify.domain.usecases.AttachmentOrganizationCoordinator {
        return com.test.blabify.domain.usecases.AttachmentOrganizationCoordinator(
            getFolders = { candidates },
            getFiles = { repo.getFilesInFolder(rootFolderId) },
            downloadText = { url ->
                com.test.blabify.data.supabase.SupabaseTextDownloader.downloadTextFromUrl(url)
            },
            moveFile = { fileId, targetId, branch, score ->
                repo.updateFileFolder(
                    fileId = fileId,
                    parentId = rootFolderId,
                    childId = targetId,
                    classificationBranch = branch,
                    classificationScore = score
                )
            },
            moveResortFile = { fileId, fromFolderId, targetId, branch, score ->
                repo.updateFileFolder(
                    fileId = fileId,
                    parentId = fromFolderId,
                    childId = targetId,
                    classificationBranch = branch,
                    classificationScore = score
                )
            },
            evaluationCore = com.test.blabify.domain.usecases.AttachmentOrganizationCore(
                baseClassifier = com.test.blabify.domain.impl.RuleBasedBaseClassifier(),
                structuralCorrector = com.test.blabify.domain.impl.DefaultStructuralCorrector()
            ),
            primaryPlacementContour = com.test.blabify.domain.usecases.PrimaryPlacementContour(),
            resortContour = com.test.blabify.domain.usecases.ResortContour()
        )
    }

}