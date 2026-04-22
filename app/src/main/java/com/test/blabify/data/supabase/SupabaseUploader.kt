package com.test.blabify.data.supabase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val supabase: SupabaseClient = createSupabaseClient(
    //размарозка
    supabaseUrl = "https://egxhazxojcqynxtlkrsb.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVneGhhenhvamNxeW54dGxrcnNiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY4NDM4MzksImV4cCI6MjA5MjQxOTgzOX0.fS59YE_qy-5OGbdOForFR9frn4cp6VQe3ARS94fVqMg"
) {
    install(Storage)
}

suspend fun uploadFileToSupabase(
    context: Context,
    fileUri: Uri,
    chatRoomId: String
): String? = withContext(Dispatchers.IO) {
    Log.d("SupabaseUploader", "Started loading: file = ${fileUri.lastPathSegment}, chatRoomId = $chatRoomId")
    try {
        val fileName = fileUri.lastPathSegment ?: "file"
        val fileBytes = context.contentResolver.openInputStream(fileUri)?.readBytes() ?: return@withContext null

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val path = "$uid/$chatRoomId/$fileName"
        val bucket = supabase.storage.from("chat-files")
        bucket.upload(path, fileBytes)
        Log.d("SupabaseUploader", "File uploaded: $path")

        return@withContext bucket.publicUrl(path)
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}