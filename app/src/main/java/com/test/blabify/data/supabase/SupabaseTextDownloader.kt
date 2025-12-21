package com.test.blabify.data.supabase
//Качает текст из файла по url

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object SupabaseTextDownloader {

    private val BINARY_EXT = setOf("jpg","jpeg","png","gif","webp","pdf","zip","rar","7z","mp4","mov","avi")

    private fun looksBinary(url: String): Boolean {
        val ext = url.substringAfterLast('.', "").lowercase().substringBefore('?')
        return ext in BINARY_EXT
    }

    suspend fun downloadTextFromUrl(url: String, headBytes: Int = 131_072):  String = withContext(Dispatchers.IO) {
        if (looksBinary(url)) return@withContext ""
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // Попросим только первые N байт
            setRequestProperty("Range", "bytes=0-${headBytes - 1}")
            // Отключим gzip, чтобы сервер честно вернул range, а не весь сжатый поток
            setRequestProperty("Accept-Encoding", "identity")
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = 1500
            readTimeout = 1500
        }
        try {
            conn.inputStream.buffered().use { inp ->
                val buf = ByteArray(headBytes)
                val n = inp.read(buf, 0, buf.size).coerceAtLeast(0)

                // Пытаемся понять кодировку из заголовка
                val ct = conn.contentType ?: ""
                val charsetName  = ct.substringAfter("charset=", "utf-8").trim().ifEmpty { "utf-8" }

                val cs = runCatching { java.nio.charset.Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)

                String(buf, 0, n, cs) // ← здесь именно Charset

            }
        } catch (e: Exception) {
            ""
        } finally {
            conn.disconnect()
        }
    }

}