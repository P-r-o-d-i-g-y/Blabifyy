package com.test.blabify.domain.repositories
//Интерфейс-описание, чтобы domain не знал про Firebase
//Интерфейс нужен, чтобы domain (usecase-слой) не зависел от конкретных реализаций Firebase/Supabase. Это чистая архитектура.

import com.test.blabify.domain.usecases.FileAutoOrganizer

interface FirestoreRepository {
    suspend fun getChildFolderNames(parentFolderId: String): List<String>
    suspend fun getChildFolderIdByName(parentFolderId: String, folderName: String): String?
    suspend fun getFilesInFolder(parentFolderId: String): List<FileAutoOrganizer.FileEntry>
    suspend fun updateFileFolder(fileId: String, parentId: String, childId: String)
}