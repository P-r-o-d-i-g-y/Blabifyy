package com.test.blabify.domain.usecases

import android.util.Log
import com.test.blabify.domain.api.CoreEvaluationResult
import com.test.blabify.domain.models.OrganizableFile
import java.io.Serializable

/**
 * Модель предложения пересортировки для нового контура 2.
 */
data class ResortSuggestionV2(
    val fileId: String,
    val fileName: String?,
    val fromFolderId: String?,
    val fromPath: String,
    val toFolderId: String,
    val toPath: String,
    val targetBranch: String?,
    val targetScore: Double,
    val confidenceGap: Double
) : Serializable

/**
 * Контур 2: правило пользовательской пересортировки.
 * Ничего не вычисляет сам, а принимает уже посчитанный результат мозга
 * и решает, предлагать перенос или нет.
 */
class ResortContour(
    private val confidenceThreshold: Double = 0.5,
    //private val moveCooldownMs: Long = 7L * 24 * 60 * 60 * 1000,
    private val minScoreGain: Double = 0.05
) {
    sealed interface Decision {
        data object Skip : Decision

        data class Suggest(
            val suggestion: ResortSuggestionV2
        ) : Decision
    }

    fun decide(
        file: OrganizableFile,
        currentPath: String,
        evaluation: CoreEvaluationResult
    ): Decision {
        fun skip(reason: String): Decision {
            android.util.Log.d(
                "ResortContour",
                "Skip file=${file.name}, reason=$reason"
            )
            return Decision.Skip
        }
        if (file.pinned == true) {
            return skip("pinned")
        }

        /*if (file.movedAt != null && System.currentTimeMillis() - file.movedAt < moveCooldownMs) {
            return Decision.Skip
        }*/

        val best = evaluation.rankedCandidates.firstOrNull() ?: return skip("no ranked candidates")
        val confidenceGap = evaluation.decision.confidenceGap

        if (confidenceGap < confidenceThreshold) {
            return skip(
                "low confidenceGap=$confidenceGap threshold=$confidenceThreshold best=${best.folder.path}"
            )
        }

        if (file.classificationFolderId == best.folder.id) {
            return skip("best is current folder=${best.folder.path}")
        }

        val previousScore = file.classificationScore
        val scoreGain = if (previousScore != null) {
            best.finalScore - previousScore
        } else {
            best.finalScore
        }

        if (previousScore != null && scoreGain < minScoreGain) {
            return skip(
                "low scoreGain=$scoreGain minScoreGain=$minScoreGain best=${best.folder.path}"
            )
        }

        val targetBranch = best.folder.path
            .split('/')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
        android.util.Log.d(
            "ResortContour",
            "Suggest file=${file.name}, from=$currentPath, to=${best.folder.path}, " +
                    "score=${best.finalScore}, gap=$confidenceGap"
        )

        return Decision.Suggest(
            ResortSuggestionV2(
                fileId = file.id,
                fileName = file.name,
                fromFolderId = file.classificationFolderId,
                fromPath = currentPath,
                toFolderId = best.folder.id,
                toPath = best.folder.path,
                targetBranch = targetBranch,
                targetScore = best.finalScore,
                confidenceGap = confidenceGap
            )
        )
    }
}