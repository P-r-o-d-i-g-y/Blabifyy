package com.test.blabify.presentation.ui

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.test.blabify.R
import com.test.blabify.presentation.adapters.TreeAdapter
import com.test.blabify.domain.models.TreeItem
import com.test.blabify.domain.models.TreeItem.NodeType
import java.util.UUID
import androidx.appcompat.app.AlertDialog

class Folders : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TreeAdapter
    private val db by lazy { FirebaseFirestore.getInstance() }

    // будем помнить последнюю раскрытую папку (для кнопки "создать")
    private var lastExpandedFolderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_folders)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            finish() // Закрывает и возвращает на предыдущую активити
        }

        recyclerView = findViewById(R.id.recycler_view_folders)  // Инициализируем RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)   // Настроить layoutManager

        recyclerView.itemAnimator?.apply {
            addDuration = 120
            removeDuration = 120
            moveDuration = 120
            changeDuration = 120
        }

        adapter = TreeAdapter(
            context = this,
            db = db,
            onFolderToggle = { id, expanded ->
                if (expanded) lastExpandedFolderId = id
                // можно и обнулить, когда закрыли последнюю, но удобнее хранить последнюю открытую
            }
        )
        recyclerView.adapter = adapter


        // Загрузка папок из Firestore
        loadRootFolders()

        findViewById<ImageButton>(R.id.crate_button).setOnClickListener {
            val targetParentId = lastExpandedFolderId
            if (targetParentId == null) {
                Toast.makeText(this, "Сначала откройте папку для создания подпапки", Toast.LENGTH_SHORT).show()
            } else {
                createNewFolderInParent(targetParentId)
            }
        }


    }
    private fun loadRootFolders() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Не удалось определить пользователя", Toast.LENGTH_SHORT).show()
            return
        }

        // Запрашиваем папки, где createdBy соответствует текущему UID
        db.collection("folders")
            .whereEqualTo("createdBy", uid)
            .whereEqualTo("parentId", null) // только корень
            .get()
            .addOnSuccessListener { result ->
                val roots = result.documents.map { doc ->
                    TreeItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "(без имени)",
                        parentId = null,
                        level = 0,
                        type = NodeType.FOLDER,
                        isExpanded = false
                    )
                }.sortedBy { it.name.lowercase() }

                adapter.submitRoot(roots)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка загрузки папок", Toast.LENGTH_SHORT).show()
                Log.e("FIRESTORE_DEBUG", "Error loading root folders: ${it.message}")
            }
    }

    private fun createNewFolderInParent(parentId: String) {
        val input = EditText(this).apply { hint = "Введите название папки" }

        AlertDialog.Builder(this)
            .setTitle("Новая папка")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val folderName = input.text.toString().trim()
                if (folderName.isEmpty()) {
                    Toast.makeText(this, "Название не может быть пустым", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newFolderId = UUID.randomUUID().toString()
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                val newFolderData = mapOf(
                    "id" to newFolderId,
                    "name" to folderName,
                    "parentId" to parentId,
                    "createdBy" to uid,
                    "createdAt" to System.currentTimeMillis()
                )


                // единственная запись — верхний документ в /folders
                db.collection("folders").document(newFolderId)
                    .set(newFolderData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Папка создана", Toast.LENGTH_SHORT).show()
                        // перечитать детей по parentId, не сворачивая родителя
                        adapter.refreshFolder(parentId)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Ошибка создания папки", Toast.LENGTH_SHORT).show()
                    }

            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}