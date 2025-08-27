package com.test.blabify.data.repositories
//Реальная работа с Firestore

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.test.blabify.domain.models.Folder
import com.test.blabify.domain.repositories.FirestoreRepository
import com.test.blabify.domain.usecases.FileAutoOrganizer
import kotlinx.coroutines.tasks.await

class FirestoreRepositoryImpl : FirestoreRepository {

    private val db = Firebase.firestore

    /** Имена дочерних папок: /folders where parentId == parentFolderId */
    override suspend fun getChildFolderNames(parentFolderId: String): List<String> {
        val snap = db.collection("folders")
            .whereEqualTo("parentId", parentFolderId)
            .get()
            .await()

        return snap.documents.mapNotNull { it.getString("name") }
    }

    /** Id дочерней папки по имени: /folders where parentId == ... and name == ...  */
    override suspend fun getChildFolderIdByName(parentFolderId: String, folderName: String): String? {
        val snap = db.collection("folders")
            .whereEqualTo("parentId", parentFolderId)
            .whereEqualTo("name", folderName)
            .limit(1)
            .get()
            .await()

        return snap.documents.firstOrNull()?.id
    }

    /** Файлы папки: /folders/{parentFolderId}/files */
    override suspend fun getFilesInFolder(parentFolderId: String): List<FileAutoOrganizer.FileEntry> {
        val snap = db.collection("folders")
            .document(parentFolderId)
            .collection("files")
            .get()
            .await()

        return snap.documents.mapNotNull {
            val id = it.id
            val url = it.getString("url") ?: return@mapNotNull null
            val name = it.getString("name")
            val pinned = it.getBoolean("pinned")
            val movedAt = it.getLong("movedAt")

            FileAutoOrganizer.FileEntry(
                id = id,
                url = url,
                name = name,
                pinned = pinned,
                movedAt = movedAt
            )
        }
    }

    /**
     * Перемещение файла: из /folders/{parentId}/files/{fileId}
     *                     в /folders/{childId}/files/{fileId}
     */
    override suspend fun updateFileFolder(fileId: String, parentId: String, childId: String) {
        val parentFiles = db.collection("folders").document(parentId).collection("files")
        val childFiles  = db.collection("folders").document(childId).collection("files")

        Log.d("FirestoreRepository", "Move file=$fileId from $parentId -> $childId")

        // Транзакция: читаем — пишем — удаляем
        db.runTransaction { tx ->
            val oldRef  = parentFiles.document(fileId)
            val oldSnap = tx.get(oldRef)
            if (!oldSnap.exists()) {
                Log.e("FirestoreRepository", "File $fileId not found at folders/$parentId/files")
                return@runTransaction null
            }
            val data = oldSnap.data ?: return@runTransaction null
            val newRef = childFiles.document(fileId)

            tx.set(newRef, data)
            tx.delete(oldRef)
            null
        }.await()
    }
    // --- НОВОЕ: дети по parentId ---
    override suspend fun getChildrenFolders(parentId: String?): List<Folder> {
        val q = if (parentId == null)
            db.collection("folders").whereEqualTo("parentId", null)
        else
            db.collection("folders").whereEqualTo("parentId", parentId)

        val snap = q.get().await()
        return snap.documents.map { doc ->
            Folder(
                id = doc.id,
                name = doc.getString("name") ?: "(без имени)",
                parentId = doc.getString("parentId"),
                topicId = doc.getString("topicId"),
                chatId = doc.getString("chatId"),
                createdBy = doc.getString("createdBy") ?: "",
                createdAt = (doc.getLong("createdAt") ?: System.currentTimeMillis()),
                childIds = emptyList(),      // больше НЕ используем childIds — оставим пустым
                isOpened = false
            )
        }
    }

    // --- НОВОЕ: все потомки (BFS) ---
    override suspend fun getDescendantFolders(rootId: String): List<Folder> {
        val out = mutableListOf<Folder>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)

        while (queue.isNotEmpty()) {
            val pid = queue.removeFirst()
            val snap = db.collection("folders")
                .whereEqualTo("parentId", pid)
                .get()
                .await()

            val children = snap.documents.map { doc ->
                Folder(
                    id = doc.id,
                    name = doc.getString("name") ?: "(без имени)",
                    parentId = doc.getString("parentId"),
                    topicId = doc.getString("topicId"),
                    chatId = doc.getString("chatId"),
                    createdBy = doc.getString("createdBy") ?: "",
                    createdAt = (doc.getLong("createdAt") ?: System.currentTimeMillis()),
                    childIds = emptyList(),
                    isOpened = false
                )
            }
            out += children
            children.forEach { queue.addLast(it.id) }
        }
        return out
    }
}