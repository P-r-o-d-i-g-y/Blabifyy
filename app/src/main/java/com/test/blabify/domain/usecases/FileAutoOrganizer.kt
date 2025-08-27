package com.test.blabify.domain.usecases

import android.util.Log
import com.test.blabify.domain.api.FileClassifier   // <-- добавили импорт
import com.test.blabify.domain.api.ClassificationResult

/*Цель
Автосортировка загруженных файлов по вложенным папкам одного чата так, чтобы они попадали в подходящую
подпапку и при этом не «скакали» (есть защита от лишних перемещений).*/



//Организатор — координирует процесс
class FileAutoOrganizer(
    private val getFolderNames: suspend () -> List<String>,
    private val getFiles: suspend () -> List<FileEntry>,
    private val downloadText: suspend (String) -> String,
    private val getFolderIdByName: suspend (String) -> String?,
    private val moveFile: suspend (fileId: String, folderId: String) -> Unit,
    private val classifier: FileClassifier              // <-- НОВОЕ поле
) {
    private val CONF_THRESHOLD = 0.7f
    private val MOVE_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000 // 7 дней

    suspend fun organize() {
        Log.d("Organizer", "Starting file organization")
        val folders = getFolderNames()
        val files = getFiles()
        Log.d("Organizer", "Files to organize: ${files.size}")

        for (file in files) {
            Log.d("Organizer", "Classifying file ${file.id}")

            // игнорируем закреплённые
            if (file.pinned == true) continue
            // кулдаун
            if (file.movedAt != null && System.currentTimeMillis() - file.movedAt < MOVE_COOLDOWN_MS) continue




            val text = runCatching { downloadText(file.url) }.getOrNull().orEmpty()
            val res: ClassificationResult = classifier.classify(
                fileName = file.name ?: "",
                fileText = text,
                candidateFolders = folders
            )

            val folderName = res.targetFolderName
            Log.d("Organizer", "Text classified as: $folderName")

            if (!folderName.isNullOrBlank()&& res.confidence >= CONF_THRESHOLD) {
                Log.d("Organizer", "Looking for folderId for name: $folderName")
                val folderId = getFolderIdByName(folderName)
                Log.d("Organizer", "Found folderId=$folderId for name $folderName")
                if (folderId != null) {
                    Log.d("Organizer", "File ${file.id} moved to $folderId")
                    moveFile(file.id, folderId)
                    Log.d("Organizer", "File ${file.id} analyzed. Assigned to folder: $folderName")
                }
            }
        }
    }

    data class FileEntry(
        val id: String,
        val url: String,
        val name: String? = null,
        val pinned: Boolean? = null,
        val movedAt: Long? = null
    )
}