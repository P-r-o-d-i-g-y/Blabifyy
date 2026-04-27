package com.test.blabify.domain.usecases
/**
 * Контур 1: правило первичного размещения.
 * Ничего не вычисляет сам, а принимает уже посчитанный результат мозга
 * и решает, перемещать файл или нет.
 */
import com.test.blabify.domain.api.CoreEvaluationResult
import com.test.blabify.domain.models.OrganizableFile

class PrimaryPlacementContour(
    private val confidenceThreshold: Double = 0.5,
    private val moveCooldownMs: Long = 7L * 24 * 60 * 60 * 1000
) {
    sealed interface Decision {
        data object Skip : Decision

        data class Move(
            val fileId: String,
            val targetFolderId: String,
            val targetFolderPath: String,
            val targetBranch: String?,
            val targetScore: Double
        ) : Decision
    }

    fun decide(
        file: OrganizableFile,
        evaluation: CoreEvaluationResult
    ): Decision {
        if (file.pinned == true) {
            return Decision.Skip
        }

        if (file.movedAt != null && System.currentTimeMillis() - file.movedAt < moveCooldownMs) {
            return Decision.Skip
        }

        val best = evaluation.rankedCandidates.firstOrNull() ?: return Decision.Skip
        val confidenceGap = evaluation.decision.confidenceGap

        if (confidenceGap < confidenceThreshold) {
            return Decision.Skip
        }

        val targetBranch = best.folder.path
            .split('/')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

        return Decision.Move(
            fileId = file.id,
            targetFolderId = best.folder.id,
            targetFolderPath = best.folder.path,
            targetBranch = targetBranch,
            targetScore = best.finalScore
        )
    }
}