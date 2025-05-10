package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.test.blabify.R

class Registration : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registration)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val comeInText: TextView = findViewById(R.id.come_in)
        comeInText.setOnClickListener {
            val intent = Intent(this, Autorization::class.java)
            startActivity(intent)
        }
        auth = FirebaseAuth.getInstance()

        val nameField = findViewById<EditText>(R.id.name)
        val loginField = findViewById<EditText>(R.id.login)
        val passwordField = findViewById<EditText>(R.id.password)
        val passwordRepeatField = findViewById<EditText>(R.id.password_repeat)
        val btnRegister = findViewById<ImageButton>(R.id.register)

        btnRegister.setOnClickListener {
            val name = nameField.text.toString()
            val email = loginField.text.toString()
            val password = passwordField.text.toString()
            val passwordRepeat = passwordRepeatField.text.toString()

            if (email.isNotEmpty() && password == passwordRepeat) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            val database = com.google.firebase.database.FirebaseDatabase.getInstance("https://blabify-a0665-default-rtdb.europe-west1.firebasedatabase.app/")
                            val usersRef = database.getReference("users")

                            if (uid != null) {
                                // Сохраняем имя пользователя в Realtime Database
                                usersRef.child(uid).setValue(mapOf("name" to name))
                            }

                            // регистрация успешна → переход к чату
                            val intent = Intent(this, ChatsActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Ошибка регистрации: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Проверьте ввод", Toast.LENGTH_SHORT).show()
            }
        }
    }
}