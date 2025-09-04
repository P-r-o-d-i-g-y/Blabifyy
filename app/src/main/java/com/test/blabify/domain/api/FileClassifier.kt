package com.test.blabify.domain.api

data class ClassificationResult(
    val targetFolderId: String?,     // целевая папка
    val targetFolderName: String?,   // для логов/отладки
    val confidence: Float,            // 0..1 (из margin)
    val score: Double                // сырой счёт кандидата
)

data class CandidateFolder(
    val id: String,
    val name: String,
    val level: Int,      // 0 — ребёнок корня, 1 — внук и т.д.
    val path: String     // например: "animals/puppy/Max"
)

interface FileClassifier {
    suspend fun classify(
        fileName: String,
        fileText: String?,
        candidates: List<CandidateFolder>
    ): ClassificationResult
}
//Это “контракт” для любого мозга: сегодня — правила, завтра — нейросеть.