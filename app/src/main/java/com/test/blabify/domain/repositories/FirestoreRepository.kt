package com.test.blabify.domain.repositories
//Интерфейс-описание, чтобы domain не знал про Firebase
//Интерфейс нужен, чтобы domain (usecase-слой) не зависел от конкретных реализаций Firebase/Supabase. Это чистая архитектура.

import com.test.blabify.domain.usecases.FileAutoOrganizer
import com.test.blabify.domain.models.Folder
import com.test.blabify.domain.models.OrganizableFile

interface FirestoreRepository {
    suspend fun getChildFolderNames(parentFolderId: String): List<String>
    suspend fun getChildFolderIdByName(parentFolderId: String, folderName: String): String?
    suspend fun getFilesInFolder(parentFolderId: String): List<OrganizableFile>
    // НОВАЯ сигнатура: передаём ветку и счёт
    suspend fun updateFileFolder(
        fileId: String,
        parentId: String,
        childId: String,
        classificationBranch: String?,
        classificationScore: Double?
    )
    //
    suspend fun getChildrenFolders(parentId: String?): List<Folder>
    suspend fun getDescendantFolders(rootId: String): List<Folder>

    // НОВОЕ: все файлы ветки одним запросом
    suspend fun getAllFilesForRoot(rootId: String): List<OrganizableFile>
}