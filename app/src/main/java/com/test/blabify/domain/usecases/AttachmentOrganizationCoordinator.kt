package com.test.blabify.domain.usecases

import android.util.Log
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.models.OrganizableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Единый координатор новой архитектуры.
 * Он один вызывает мозг, потом передает результат в нужный контур
 * и применяет итоговое действие.
 */
class AttachmentOrganizationCoordinator(
    private val rootFolderId: String,
    private val getFolders: suspend () -> List<CandidateFolder>,
    private val getPrimaryFiles: suspend () -> List<OrganizableFile>,
    private val getResortFiles: suspend () -> List<OrganizableFile>,
    private val downloadText: suspend (String) -> String,
    //Для 1-го контура: текущий источник один и задается снаружи
    private val moveFile: suspend (
        fileId: String,
        targetFolderId: String,
        classificationBranch: String?,
        classificationScore: Double?
    ) -> Unit,
    // Для 2-го контура: нужен source folder
    private val moveResortFile: suspend (
        fileId: String,
        fromFolderId: String,
        targetFolderId: String,
        classificationBranch: String?,
        classificationScore: Double?
    ) -> Unit,
    private val evaluationCore: AttachmentOrganizationCore,
    private val primaryPlacementContour: PrimaryPlacementContour,
    private val resortContour: ResortContour
) {
    suspend fun runPrimaryPlacement() {
        Log.d("AttachmentCoordinator", "Starting primary placement")

        val folders = getFolders()
        val files = getPrimaryFiles()
        val textsByFileId = preloadTexts(files)

        for (file in files) {
            val candidates = selectCandidates(file, folders)
            if (candidates.isEmpty()) continue

            val evaluation = evaluationCore.evaluate(
                fileName = file.name ?: "",
                fileText = textsByFileId[file.id].orEmpty(),
                candidates = candidates
            )

            when (val decision = primaryPlacementContour.decide(file, evaluation)) {
                is PrimaryPlacementContour.Decision.Skip -> Unit
                is PrimaryPlacementContour.Decision.Move -> {
                    Log.d(
                        "AttachmentCoordinator",
                        "Move ${decision.fileId} -> ${decision.targetFolderId} (${decision.targetFolderPath})"
                    )
                    moveFile(
                        decision.fileId,
                        decision.targetFolderId,
                        decision.targetBranch,
                        decision.targetScore
                    )
                }
            }
        }
    }

    suspend fun buildResortSuggestions(): List<ResortSuggestionV2> {
        Log.d("AttachmentCoordinator", "Building resort suggestions")
        val folders = getFolders()
        val files = getResortFiles()
        val textsByFileId = preloadTexts(files)
        val foldersById = folders.associateBy { it.id }

        val suggestions = mutableListOf<ResortSuggestionV2>()

        for (file in files) {
            val candidates = selectCandidates(file, folders)
            if (candidates.isEmpty()) continue

            val evaluation = evaluationCore.evaluate(
                fileName = file.name ?: "",
                fileText = textsByFileId[file.id].orEmpty(),
                candidates = candidates
            )

            val currentPath = foldersById[file.classificationFolderId]?.path ?: "(корень)"

            when (val decision = resortContour.decide(file, currentPath, evaluation)) {
                is ResortContour.Decision.Skip -> Unit
                is ResortContour.Decision.Suggest -> suggestions += decision.suggestion
            }
        }

        return suggestions
    }

    suspend fun applyResortSuggestions(
        suggestions: List<ResortSuggestionV2>
    ): Int {
        var applied = 0

        for (suggestion in suggestions) {
            val fromFolderId = suggestion.fromFolderId ?: rootFolderId

            moveResortFile(
                suggestion.fileId,
                fromFolderId,
                suggestion.toFolderId,
                suggestion.targetBranch,
                suggestion.targetScore
            )
            applied++
        }
        return applied
    }

    private suspend fun preloadTexts(
        files: List<OrganizableFile>
    ): Map<String, String> {
        val textConcurrency = 20
        val textSemaphore = Semaphore(textConcurrency)
        val textCache = mutableMapOf<String, String>()

        return coroutineScope {
            files.map { file ->
                async(Dispatchers.IO) {
                    val text = textSemaphore.withPermit {
                        textCache.getOrPut(file.url) {
                            runCatching { downloadText(file.url) }
                                .getOrNull()
                                .orEmpty()
                        }
                    }

                    file.id to text
                }
            }.awaitAll().toMap()
        }
    }

    private fun selectCandidates(
        file: OrganizableFile,
        folders: List<CandidateFolder>
    ): List<CandidateFolder> {
        return if (!file.classificationBranch.isNullOrBlank()) {
            folders.filter { candidate ->
                branchNameOf(candidate) == file.classificationBranch
            }
        } else {
            folders
        }
    }

    private fun branchNameOf(candidate: CandidateFolder?): String? {
        return candidate?.path
            ?.split('/')
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}