package com.test.blabify.presentation.ui

import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.test.blabify.R
import com.bumptech.glide.Glide
import android.os.Handler

class Splash : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        Glide.with(this)
            .asGif()
            .load(R.raw.blab) // Замените "your_loading_gif" на имя вашего GIF файла
            .into(findViewById(R.id.splashGif)) // Укажите ID вашего ImageView

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Переход на следующий экран (например, онбординг)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, Autorization::class.java) // замени при необходимости
            startActivity(intent)
            finish()
        }, 4000)
    }
}