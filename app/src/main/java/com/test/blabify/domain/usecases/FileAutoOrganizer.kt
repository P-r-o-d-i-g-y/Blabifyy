package com.test.blabify.domain.usecases

import android.util.Log

//Организатор — координирует процесс


class FileAutoOrganizer(
    private val getFolderNames: suspend () -> List<String>,
    private val getFiles: suspend () -> List<FileEntry>,
    private val downloadText: suspend (String) -> String,
    private val getFolderIdByName: suspend (String) -> String?,
    private val moveFile: suspend (fileId: String, folderId: String) -> Unit
) {
    suspend fun organize() {
        Log.d("Organizer", "Starting file organization")
        val folders = getFolderNames()
        val classifier = FileClassifier(folders)

        val files = getFiles()
        Log.d("Organizer", "Files to organize: ${files.size}")

        for (file in files) {
            Log.d("Organizer", "Classifying file ${file.id}")

            val text = downloadText(file.url)
            val folderName = classifier.classify(text)
            Log.d("Organizer", "Text classified as: $folderName")

            if (folderName != null) {
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
        val url: String
    )
}