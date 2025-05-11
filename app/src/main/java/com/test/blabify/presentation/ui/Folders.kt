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
import com.test.blabify.domain.models.Folder
import com.test.blabify.presentation.adapters.FolderAdapter
import java.util.UUID
import androidx.appcompat.app.AlertDialog

class Folders : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FolderAdapter

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

        // Загрузка папок из Firestore
        loadFoldersFromFirestore()

        findViewById<ImageButton>(R.id.crate_button).setOnClickListener {
            val openFolder = getOpenFolder() // Получаем открытую папку
            if (openFolder != null) {
                createNewFolderInParent(openFolder.id) // Создаем новую папку внутри этой
            } else {
                Toast.makeText(this, "Папка не открыта", Toast.LENGTH_SHORT).show()
            }
        }


    }
    fun loadFoldersFromFirestore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return // Получаем UID текущего пользователя
        Log.d("FIRESTORE_DEBUG", "Fetching folders for user: $uid") // Логируем UID текущего пользователя
        if (uid == null) {
            Log.e("FIRESTORE_DEBUG", "User UID is null, cannot fetch folders.")
            return
        }

        // Запрашиваем папки, где createdBy соответствует текущему UID
        FirebaseFirestore.getInstance()
            .collection("folders")
            .whereEqualTo("createdBy", uid)
            .get()
            .addOnSuccessListener { result ->
                Log.d("FIRESTORE_DEBUG", "Folders loaded successfully.")
                val folders = mutableListOf<Folder>()
                for (document in result) {
                    val folder = document.toObject(Folder::class.java) // Преобразуем документ в объект Folder
                    folders.add(folder)
                }
                // Используем уже инициализированный recyclerView
                adapter = FolderAdapter(folders) {
                    // Обработка клика по папке
                    Toast.makeText(this, "Clicked on folder: ${it.name}", Toast.LENGTH_SHORT).show()
                }

                recyclerView.adapter = adapter  // Устанавливаем адаптер
            }
            .addOnFailureListener{ exception ->
                Log.e("FIRESTORE_DEBUG", "Error loading folders: ${exception.message}")
                Toast.makeText(this, "Error loading folders", Toast.LENGTH_SHORT).show()
            }
    }

    fun getOpenFolder(): Folder? {
        return (adapter as FolderAdapter).folders.find { it.isOpened }
    }

    fun createNewFolderInParent(parentId: String?) {
        val input = EditText(this)
        input.hint = "Введите название папки"

        AlertDialog.Builder(this)
            .setTitle("Новая папка")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val folderName = input.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    val newFolderId = UUID.randomUUID().toString()
                    val newFolder = Folder(
                        id = newFolderId,
                        name = folderName,
                        parentId = parentId,
                        createdBy = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown"
                    )

                    // Добавляем новую папку в Firestore
                    FirebaseFirestore.getInstance()
                        .collection("folders")
                        .document(parentId ?: "")  // Идентификатор родительской папки
                        .collection("childFolders") // Вложенная коллекция для дочерних папок
                        .document(newFolderId) // Уникальный ID дочерней папки
                        .set(newFolder)
                        .addOnSuccessListener {
                            // Обновляем родительскую папку, добавляем ID новой папки в childIds
                            if (parentId != null) {
                                FirebaseFirestore.getInstance()
                                    .collection("folders")
                                    .document(parentId)
                                    .update("childIds", FieldValue.arrayUnion(newFolderId))
                            }
                            Toast.makeText(this, "Папка создана", Toast.LENGTH_SHORT).show()
                            loadFoldersFromFirestore() // Обновляем список папок
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Ошибка создания папки", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Название папки не может быть пустым", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}