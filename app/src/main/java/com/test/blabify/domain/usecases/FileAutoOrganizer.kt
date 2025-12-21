package com.test.blabify.domain.usecases

import android.util.Log
import com.test.blabify.domain.api.FileClassifier
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.ClassificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/*Цель
Автосортировка загруженных файлов по вложенным папкам одного чата так, чтобы они попадали в подходящую
подпапку и при этом не «скакали» (есть защита от лишних перемещений).*/
/**
 * Организатор: получает список кандидатов (id, name, level, path),
 * классифицирует и перемещает файл в папку по ID.
 * Есть "липкость к ветке": если файл уже привязан к ветке, рассматриваем
 * только кандидатов ВНУТРИ этой ветки.
 */


class FileAutoOrganizer(
    private val getFolders: suspend () -> List<CandidateFolder>,
    private val getFiles: suspend () -> List<FileEntry>,
    private val downloadText: suspend (String) -> String,
    private val moveFile: suspend (
        fileId: String,
        targetFolderId: String,
        classificationBranch: String?,
        classificationScore: Double?
    ) -> Unit,
    private val classifier: FileClassifier
) {
    private val CONF_THRESHOLD = 0.5f
    private val MOVE_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000 // 7 дней

    suspend fun organize() {
        Log.d("Organizer", "Starting file organization")
        val folders = getFolders()
        val files = getFiles()
        Log.d("Organizer", "Files=${files.size}, candidates=${folders.size}")

        // параллельно скачиваем тексты для всех файлов
        val textConcurrency = 20
        val textSemaphore = Semaphore(textConcurrency)
        val textCache = mutableMapOf<String, String>()

        val textsByFileId: Map<String, String> = coroutineScope {
            files.map { file ->
                async(Dispatchers.IO) {
                    textSemaphore.withPermit {
                        textCache.getOrPut(file.url) {
                            runCatching { downloadText(file.url) }
                                .getOrNull()
                                .orEmpty()
                        }
                    }.let { text -> file.id to text }
                }
            }.awaitAll().toMap()
        }




        // быстрые хелперы
        val byId = folders.associateBy { it.id }
        fun branchNameOf(c: CandidateFolder?): String? =
            c?.path?.split('/')?.firstOrNull()

        for (file in files) {
            Log.d("Organizer", "Classifying file ${file.id}")

            // игнорируем закреплённые
            if (file.pinned == true) continue
            // кулдаун
            if (file.movedAt != null && System.currentTimeMillis() - file.movedAt < MOVE_COOLDOWN_MS) continue

            // кандидаты: если есть привязка к ветке — фильтруем
            val candidates =
                if (!file.classificationBranch.isNullOrBlank())
                    folders.filter { cand -> branchNameOf(cand) == file.classificationBranch }
                else
                    folders

            if (candidates.isEmpty()) continue

            val text = textsByFileId[file.id].orEmpty()
            val res: ClassificationResult = classifier.classify(
                fileName = file.name ?: "",
                fileText = text,
                candidates = candidates
            )

            val target = byId[res.targetFolderId]
            if (target == null) {
                Log.d("Organizer", "Skip ${file.id}: target is null")
                continue
            }

            if (res.confidence >= CONF_THRESHOLD) {
                val targetBranch = branchNameOf(target)
                Log.d(
                    "Organizer",
                    "Move ${file.id} -> ${target.id} (${target.path}), conf=${res.confidence}, score=${res.score}"
                )
                // ВАЖНО: вызываем moveFile с 4 аргументами
                moveFile(file.id, target.id, targetBranch, res.score)
            } else {
                Log.d("Organizer", "Skip ${file.id}: low confidence (${res.confidence})")
            }
        }
    }

    data class FileEntry(
        val id: String,
        val url: String,
        val name: String? = null,
        val pinned: Boolean? = null,
        val movedAt: Long? = null,

        // НОВОЕ: для политики перемещений
        val classificationBranch: String? = null,   // имя первой папки (ветка), напр. "puppy"
        val classificationFolderId: String? = null, // куда в последний раз положили
        val classificationScore: Double? = null     // последний score
    )
}