package com.test.blabify.data

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val supabase: SupabaseClient = createSupabaseClient(
    supabaseUrl = "https://dhzpebnessobssrrheiy.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRoenBlYm5lc3NvYnNzcnJoZWl5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwNjA5ODEsImV4cCI6MjA2MjYzNjk4MX0.E6FWq-pzy_RFNEFSlL80mCBqdWatZGv21nIOjBiUSMw"
) {
    install(Storage)
}

suspend fun uploadFileToSupabase(
    context: Context,
    fileUri: Uri,
    chatRoomId: String
): String? = withContext(Dispatchers.IO) {
    try {
        val fileName = fileUri.lastPathSegment ?: "file"
        val fileBytes = context.contentResolver.openInputStream(fileUri)?.readBytes() ?: return@withContext null

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val path = "$uid/$chatRoomId/$fileName"
        val bucket = supabase.storage.from("chat-files")
        bucket.upload(path, fileBytes)

        return@withContext bucket.publicUrl(path)
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext null
    }
}