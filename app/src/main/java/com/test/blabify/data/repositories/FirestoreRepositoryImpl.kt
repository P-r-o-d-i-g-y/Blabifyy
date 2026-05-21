package com.test.blabify.data.repositories
//Реальная работа с Firestore

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.test.blabify.domain.models.Folder
import com.test.blabify.domain.models.OrganizableFile
import com.test.blabify.domain.repositories.FirestoreRepository
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
    override suspend fun getFilesInFolder(parentFolderId: String): List<OrganizableFile> {
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
            val classificationBranch = it.getString("classificationBranch")
            val classificationFolderId = it.getString("classificationFolderId")
            val classificationScore = it.getDouble("classificationScore")

            OrganizableFile(
                id = id,
                url = url,
                name = name,
                pinned = pinned,
                movedAt = movedAt,
                classificationBranch = classificationBranch,
                classificationFolderId = classificationFolderId,
                classificationScore = classificationScore
            )
        }
    }

    /**
     * Перемещение файла: из /folders/{parentId}/files/{fileId}
     *                     в /folders/{childId}/files/{fileId}
     */
    override suspend fun updateFileFolder(
        fileId: String,
        parentId: String,
        childId: String,
        classificationBranch: String?,
        classificationScore: Double?
    ) {
        val parentFiles = db.collection("folders").document(parentId).collection("files")
        val childFiles  = db.collection("folders").document(childId).collection("files")

        Log.d("FirestoreRepository", "Move file=$fileId from $parentId -> $childId")

        db.runTransaction { tx ->
            val oldRef  = parentFiles.document(fileId)
            val oldSnap = tx.get(oldRef)
            if (!oldSnap.exists()) {
                Log.e("FirestoreRepository", "File $fileId not found at folders/$parentId/files")
                return@runTransaction null
            }

            // берём существующие поля и делаем их изменяемыми
            val data = (oldSnap.data ?: return@runTransaction null).toMutableMap()

            // ⬇⬇⬇ ВАЖНО: обновляем метаданные политики перемещения
            val now = System.currentTimeMillis()
            data["movedAt"] = now
            data["classificationFolderId"] = childId
            if (classificationBranch != null) data["classificationBranch"] = classificationBranch
            if (classificationScore != null)  data["classificationScore"]  = classificationScore
            // ⬆⬆⬆

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

    //все потомки (BFS)
    override suspend fun getDescendantFolders(rootId: String): List<Folder> {
        val snap = db.collection("folders")
            .whereEqualTo("rootFolderId", rootId)
            .get()
            .await()

        return snap.documents
            .map { doc ->
                Folder(
                    id = doc.id,
                    name = doc.getString("name") ?: "(без имени)",
                    parentId = doc.getString("parentId"),
                    topicId = doc.getString("topicId"),
                    chatId = doc.getString("chatId"),
                    createdBy = doc.getString("createdBy") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    childIds = emptyList(),
                    isOpened = false
                )
            }
            .filter { it.id != rootId }
    }
    //для одного запроса по всем файлам ветки
    override suspend fun getAllFilesForRoot(rootId: String): List<OrganizableFile> {
        val snap = db.collectionGroup("files")
            .whereEqualTo("rootFolderId", rootId)
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            val id = doc.id
            val url = doc.getString("url") ?: return@mapNotNull null
            val name = doc.getString("name")
            val pinned = doc.getBoolean("pinned")
            val movedAt = doc.getLong("movedAt")
            val classificationBranch = doc.getString("classificationBranch")
            val classificationFolderId = doc.getString("classificationFolderId")
            val classificationScore = doc.getDouble("classificationScore")

            OrganizableFile(
                id = id,
                url = url,
                name = name,
                pinned = pinned,
                movedAt = movedAt,
                classificationBranch = classificationBranch,
                classificationFolderId = classificationFolderId,
                classificationScore = classificationScore
            )
        }
    }
}