package com.test.blabify.presentation.ui

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.test.blabify.R
import com.test.blabify.domain.models.Folder
import com.test.blabify.presentation.adapters.FolderAdapter

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
}