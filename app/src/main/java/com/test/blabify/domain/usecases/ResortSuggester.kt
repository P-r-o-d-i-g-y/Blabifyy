package com.test.blabify.domain.usecases

import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.FileClassifier
import com.test.blabify.domain.impl.RuleBasedClassifier
import com.test.blabify.domain.repositories.FirestoreRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ResortSuggestion(
    val fileId: String,
    val fileName: String?,
    val fromPath: String,   // старый путь (от корня чата), "(корень)" если нет
    val toPath: String      // новый путь (от корня чата)
) : java.io.Serializable

/**
 * Строит dry-run предложения по перемещениям внутри ОДНОГО чата.
 * - Pinned — игнорируем
 * - Кулдаун — учитываем
 * - Липкость к «внутренней ветке» (папка 1-го уровня) — соблюдаем
 * - Перепривязка ветки разрешена только при сильном перевесе (DELTA/RATIO)
 * - Неоднозначность (MARGIN) — пропускаем
 */
class ResortSuggester(
    private val repo: FirestoreRepository,
    private val classifier: FileClassifier = RuleBasedClassifier(),
    private val downloadText: suspend (String) -> String
) {
    private val MOVE_COOLDOWN_MS = 0L //7L * 24 * 60 * 60 * 1000 // на время тестов, а нужен ли вообще кулдавн?
    private val CONF = 0.5f
    private val MARGIN = 0.3
    private val DELTA = 0.8
    private val RATIO = 1.5

    suspend fun buildForChatRoot(rootFolderId: String): List<ResortSuggestion> {
        val now = System.currentTimeMillis()

        // 1) все потомки + индекс
        val descendants = repo.getDescendantFolders(rootFolderId)
        val byId = descendants.associateBy { it.id }

        fun pathOf(folderId: String?): String {
            if (folderId == null) return ""
            val chain = ArrayList<String>()
            var cur = folderId
            while (true) {
                val f = byId[cur] ?: break
                chain += f.name
                if (f.parentId == rootFolderId) break
                cur = f.parentId ?: break
            }
            return chain.asReversed().joinToString("/")
        }
        fun branchOfPath(path: String): String? =
            path.substringBefore('/', path).ifBlank { null }

        // 2) кандидаты (как и раньше) — из ВСЕХ потомков (корень не кандидат)
        val candidates = descendants.map { f ->
            val p = pathOf(f.id)
            CandidateFolder(
                id = f.id,
                name = f.name,
                level = p.count { it == '/' }, // 0 — ребёнок корня
                path  = p                       // "puppy/Max"
            )
        }
        val candById = candidates.associateBy { it.id }

        // 3) собрать файлы из КАЖДОЙ папки (и из корня)
        data class FileWithPlace(
            val file: FileAutoOrganizer.FileEntry,
            val folderId: String           // где он сейчас лежит
        )

        val allFiles = repo.getAllFilesForRoot(rootFolderId)

        val filesWithPlace = allFiles.map { f ->
            FileWithPlace(
                file = f,
                folderId = f.classificationFolderId ?: rootFolderId // костяль если папка не определенна
            )
        }

        // 4) заранее скачать тексты файлов параллельно с ограничением (до val out = mutableListOf<ResortSuggestion>())
        val textConcurrency = 20
        val textSemaphore = Semaphore(textConcurrency)
        val textCache = mutableMapOf<String, String>()

        val textsByFileId: Map<String, String> = coroutineScope {
            filesWithPlace.map { (file, _) ->
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


        val out = mutableListOf<ResortSuggestion>()

        // helper для top-2 маржи
        fun top2(scores: List<Double>): Pair<Double, Double> {
            var a = Double.NEGATIVE_INFINITY
            var b = Double.NEGATIVE_INFINITY
            for (s in scores) {
                if (s > a) { b = a; a = s } else if (s > b) { b = s }
            }
            return a to b
        }

        for ((file, currentFolderId) in filesWithPlace) {
            // 4) игноры
            if (file.pinned == true) continue
            if (file.movedAt != null && now - file.movedAt < MOVE_COOLDOWN_MS) continue

            val fileText = textsByFileId[file.id].orEmpty()

            // текущая ветка: сначала из метаданных,
            // если пусто — берём по текущему расположению файла
            val currentPath   = pathOf(file.classificationFolderId ?: currentFolderId)
            val currentBranch = file.classificationBranch ?: branchOfPath(currentPath)

            // кандидаты внутри ветки (или все, если ветки нет)
            // кандидаты внутри ветки (или все, если ветки нет) — с кешем branch
            val branchCache = mutableMapOf<String, String?>()
            fun branchCached(p: String) = branchCache.getOrPut(p) { branchOfPath(p) }

            val inBranch = if (!currentBranch.isNullOrBlank())
                candidates.filter { branchCached(it.path) == currentBranch }
            else candidates
            if (inBranch.isEmpty()) continue

            // лучший в своей ветке
            val resIn = classifier.classify(file.name ?: "", fileText, inBranch)
            if (resIn.confidence < CONF) continue

            // маржа внутри ветки
            run {
                // Если у нас RuleBasedClassifier — считаем баллы напрямую
                val rule = (classifier as? RuleBasedClassifier)
                val textLower = ((file.name ?: "") + " " + fileText).lowercase()

                val scoresIn: List<Double> = if (rule != null) {
                    inBranch.map { c -> rule.score(textLower, c) }
                } else {
                    // Fallback для других реализаций FileClassifier
                    inBranch.map { c ->
                        classifier.classify(file.name ?: "", fileText, listOf(c)).score
                    }
                }

                val (a, b) = top2(scoresIn)
                val margin = if (a.isFinite() && b.isFinite()) a - b else 999.0
                if (margin < MARGIN) return@run
            }

            val targetIn = candById[resIn.targetFolderId] ?: continue
            val fromPath = currentPath

            // попробовать глобального победителя, чтобы разрешить смену ветки,
            // если перевес большой
            val resGlobal = classifier.classify(file.name ?: "", fileText, candidates)
            val targetGlobal = candById[resGlobal.targetFolderId]
            val wantChangeBranch =
                !currentBranch.isNullOrBlank() &&
                        targetGlobal != null &&
                        branchOfPath(targetGlobal.path) != currentBranch &&
                        resGlobal.score >= resIn.score + DELTA &&
                        (if (resIn.score <= 0.0) true else resGlobal.score / resIn.score >= RATIO)

            val finalTarget = if (wantChangeBranch) targetGlobal!! else targetIn

            // тот же лист — не предлагаем
            if (file.classificationFolderId == finalTarget.id) continue

            out += ResortSuggestion(
                fileId   = file.id,
                fileName = file.name,
                fromPath = if (fromPath.isBlank()) "(корень)" else fromPath,
                toPath   = finalTarget.path
            )
        }
        return out
    }
}