package com.test.blabify.data.supabase
//Качает текст из файла по url

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object SupabaseTextDownloader {

    suspend fun downloadTextFromUrl(url: String):  String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            connection.disconnect()
        }
    }
}