package com.test.blabify.domain.api

data class ClassificationResult(
    val targetFolderName: String?, // null — если не уверены
    val confidence: Float          // 0..1
)

interface FileClassifier {
    suspend fun classify(
        fileName: String,
        fileText: String?,
        candidateFolders: List<String>
    ): ClassificationResult
}
//Это “контракт” для любого мозга: сегодня — правила, завтра — нейросеть.