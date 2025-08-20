package com.test.blabify.data.repositories
//Реальная работа с Firestore

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.test.blabify.domain.repositories.FirestoreRepository
import com.google.firebase.firestore.FieldPath
import com.test.blabify.domain.usecases.FileAutoOrganizer
import kotlinx.coroutines.tasks.await

class FirestoreRepositoryImpl : FirestoreRepository {

    private val db = Firebase.firestore

    override suspend fun getChildFolderNames(parentFolderId: String): List<String> {
        val snapshot = db.collection("folders")
            .document(parentFolderId)
            .collection("childFolders")
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.getString("name") }
    }

    override suspend fun getChildFolderIdByName(parentFolderId: String, folderName: String): String? {
        val snapshot = db.collection("folders")
            .document(parentFolderId)
            .collection("childFolders")
            .whereEqualTo("name", folderName)
            .get()
            .await()

        return snapshot.documents.firstOrNull()?.id
    }

    override suspend fun getFilesInFolder(parentFolderId: String): List<FileAutoOrganizer.FileEntry> {
        val snapshot = db.collection("folders")
            .document(parentFolderId)
            .collection("files")
            .get()
            .await()

        return snapshot.documents.mapNotNull {
            val id = it.id
            val url = it.getString("url")
            val name = it.getString("name") // <-- берём имя из Firestore
            if (url != null) FileAutoOrganizer.FileEntry(id, url, name) else null
        }
    }

    override suspend fun updateFileFolder(fileId: String, parentId: String, childId: String) {
        Log.d("FirestoreRepository", "Searching for fileId=$fileId in collectionGroup")

        val fileSnapshot = db.collection("folders")
            .document(parentId)
            .collection("files")
            .document(fileId)
            .get()
            .await()

        if (!fileSnapshot.exists()) {
            Log.e("FirestoreRepository", "File $fileId not found in folders/$parentId/files")
            return
        }

        val data = fileSnapshot?.data ?: return
        Log.d("FirestoreRepository", "Deleting file $fileId from old location")
        fileSnapshot.reference.delete().await()

        Log.d("FirestoreRepository", "Creating file $fileId in child folder $childId")

        Log.d("FirestoreRepository", "Set path: folders/$parentId/childFolders/$childId/files/$fileId")


        db.collection("folders")
            .document(parentId)
            .collection("childFolders")
            .document(childId)
            .collection("files")
            .document(fileId)
            .set(data)
            .await()
    }
}