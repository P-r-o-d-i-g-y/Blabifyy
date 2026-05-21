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

    suspend fun handleNewFileUploaded() {
        runPrimaryPlacement()
    }

    suspend fun runPrimaryPlacement() {
        Log.d("AttachmentCoordinator", "Starting primary placement")

        val folders = getFolders()
        val files = getPrimaryFiles()
        val textsByFileId = preloadTexts(files)
        Log.d(
            "AttachmentCoordinator",
            "Primary input: folders=${folders.size}, files=${files.size}, " +
                    "candidatePaths=${folders.joinToString { it.path }}"
        )
        for (file in files) {
            if (folders.isEmpty()) {
                Log.d("AttachmentCoordinator", "Primary skip: no folders")
                continue
            }
            val fileText = textsByFileId[file.id].orEmpty()

            Log.d(
                "AttachmentCoordinator",
                "Primary file=${file.name}, textBlank=${fileText.isBlank()}, " +
                        "text=${fileText.take(300).replace('\n', ' ')}"
            )
            if (fileText.isBlank()) {
                Log.d(
                    "AttachmentCoordinator",
                    "Primary skip: file=${file.name}, reason=text is empty or failed to download"
                )
                continue
            }
            val evaluation = evaluationCore.evaluate(
                fileText = textsByFileId[file.id].orEmpty(),
                candidates = folders
            )
            val top = evaluation.rankedCandidates
                .take(5)
                .joinToString {
                    "${it.folder.path}: base=${it.baseScore}, " +
                            "corr=${it.structuralCorrection}, final=${it.finalScore}"
                }

            Log.d(
                "AttachmentCoordinator",
                "Primary best for ${file.name}: " +
                        "best=${evaluation.decision.best?.folder?.path}, " +
                        "gap=${evaluation.decision.confidenceGap}, top=$top"
            )
            when (val decision = primaryPlacementContour.decide(file, evaluation)) {
                is PrimaryPlacementContour.Decision.Skip -> Unit
                is PrimaryPlacementContour.Decision.Move -> {
                    Log.d(
                        "AttachmentCoordinator",
                        "Primary move ${decision.fileId} -> " +
                                "${decision.targetFolderId} (${decision.targetFolderPath})"
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
        Log.d(
            "AttachmentCoordinator",
            "Resort input: folders=${folders.size}, files=${files.size}"
        )
        Log.d(
            "AttachmentCoordinator",
            "Resort folders: ${folders.joinToString { it.path }}"
        )
        val textsByFileId = preloadTexts(files)
        val foldersById = folders.associateBy { it.id }

        val suggestions = mutableListOf<ResortSuggestionV2>()

        for (file in files) {
            Log.d(
                "AttachmentCoordinator",
                "Resort file=${file.name}, branch=${file.classificationBranch}, " +
                        "folderId=${file.classificationFolderId}, prevScore=${file.classificationScore}, " +
                        "candidates=${folders.size}, candidatePaths=${folders.joinToString { it.path }}"
            )
            if (folders.isEmpty()) continue

            val fileText = textsByFileId[file.id].orEmpty()

            if (fileText.isBlank()) {
                Log.d(
                    "AttachmentCoordinator",
                    "Resort skip: file=${file.name}, reason=text is empty or failed to download"
                )
                continue
            }

            Log.d(
                "AttachmentCoordinator",
                "Resort text preview: file=${file.name}, " +
                        "folderId=${file.classificationFolderId}, " +
                        "branch=${file.classificationBranch}, " +
                        "url=${file.url}, " +
                        "text=${fileText.take(500).replace('\n', ' ')}"
            )

            val evaluation = evaluationCore.evaluate(
                fileText = fileText,
                candidates = folders
            )

            val top = evaluation.rankedCandidates
                .take(5)
                .joinToString { "${it.folder.path}:${it.finalScore}" }

            Log.d(
                "AttachmentCoordinator",
                "Resort best for ${file.name}: " +
                        "best=${evaluation.rankedCandidates.firstOrNull()?.folder?.path}, " +
                        "gap=${evaluation.decision.confidenceGap}, top=$top"
            )

            val currentPath = foldersById[file.classificationFolderId]?.path ?: "(корень)"

            when (val decision = resortContour.decide(file, currentPath, evaluation)) {
                is ResortContour.Decision.Skip -> {
                    Log.d("AttachmentCoordinator", "Resort skip: file=${file.name}")
                }
                is ResortContour.Decision.Suggest -> {
                    Log.d(
                        "AttachmentCoordinator",
                        "Resort suggest: ${decision.suggestion.fileName} " +
                                "${decision.suggestion.fromPath} -> ${decision.suggestion.toPath}"
                    )
                    suggestions += decision.suggestion
                }
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

    //@удалить
    /*private fun selectCandidates(
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
    }*/

    private fun branchNameOf(candidate: CandidateFolder?): String? {
        return candidate?.path
            ?.split('/')
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}