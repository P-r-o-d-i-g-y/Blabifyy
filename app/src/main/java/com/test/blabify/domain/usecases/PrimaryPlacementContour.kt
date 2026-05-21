package com.test.blabify.domain.usecases
/**
 * Контур 1: правило первичного размещения.
 * Ничего не вычисляет сам, а принимает уже посчитанный результат мозга
 * и решает, перемещать файл или нет.
 */
import android.util.Log
import com.test.blabify.domain.api.CoreEvaluationResult
import com.test.blabify.domain.models.OrganizableFile

class PrimaryPlacementContour(
    private val rootScore: Double = 0.0,

    /**
     * Минимальная выгода относительно корня.
     *
     * Если кандидат лучше root хотя бы на 0.05,
     * новый файл можно размещать.
     */
    private val minRootMoveGain: Double = 0.05,

    /**
     * Минимальный разрыв между лучшим и вторым кандидатом.
     *
     * Для первичного размещения он должен быть мягче,
     * чем для пересортировки.
     */
    //private val minConfidenceGap: Double = 0.05,

    /**
     * Если второй кандидат вообще не набрал score,
     * не считаем ситуацию спорной.
     */
    private val zeroScoreEpsilon: Double = 0.000001
    //private val moveCooldownMs: Long = 7L * 24 * 60 * 60 * 1000
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
        fun skip(reason: String): Decision {
            Log.d(
                "PrimaryPlacementContour",
                "Skip file=${file.name}, reason=$reason"
            )
            return Decision.Skip
        }

        val best = evaluation.decision.best
            ?: return skip("no best candidate")

        //val secondBest = evaluation.decision.secondBest
        //val confidenceGap = evaluation.decision.confidenceGap
        val rootMoveGain = best.finalScore - rootScore

        if (rootMoveGain < minRootMoveGain) {
            return skip(
                "best is not better than root enough: " +
                        "best=${best.folder.path}, " +
                        "bestScore=${best.finalScore}, " +
                        "rootScore=$rootScore, " +
                        "gain=$rootMoveGain, " +
                        "requiredGain=$minRootMoveGain"
            )
        }

        /*val secondHasRealScore =
            secondBest != null && secondBest.finalScore > zeroScoreEpsilon

        if (secondHasRealScore && confidenceGap < minConfidenceGap) {
            return skip(
                "confidence gap too small: " +
                        "best=${best.folder.path}, " +
                        "second=${secondBest?.folder?.path}, " +
                        "gap=$confidenceGap, " +
                        "requiredGap=$minConfidenceGap"
            )
        }*/

        val targetBranch = best.folder.path
            .split('/')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

        Log.d(
            "PrimaryPlacementContour",
            "Move file=${file.name}, " +
                    "target=${best.folder.path}, " +
                    "score=${best.finalScore}, " +
                    "rootMoveGain=$rootMoveGain, " +
                    "gap=${evaluation.decision.confidenceGap}"
        )

        return Decision.Move(
            fileId = file.id,
            targetFolderId = best.folder.id,
            targetFolderPath = best.folder.path,
            targetBranch = targetBranch,
            targetScore = best.finalScore
        )
    }
}