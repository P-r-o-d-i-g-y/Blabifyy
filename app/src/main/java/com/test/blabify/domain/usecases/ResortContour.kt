package com.test.blabify.domain.usecases

import android.util.Log
import com.test.blabify.domain.api.AdjustedCandidateScore
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
 *
 * Он принимает уже посчитанный рейтинг папок и решает,
 * стоит ли предложить пользователю перенос.
 */
class ResortContour(
    //private val rootMinBestScore: Double = 0.5,
    /**
     * Минимальный выигрыш, чтобы разрешить перенос между разными ветками.
     *
     * Нужен, чтобы не вытаскивать файл из уже существующей папки
     * в другую ветку из-за слабого/общего совпадения.
     */
    private val minCrossBranchMoveGain: Double = 1.0,

    /**
     * Запрещает перенос между разными ветками, если целевая папка
     * является более общей папкой с тем же последним сегментом.
     *
     * Пример:
     * dog/puppy -> puppy запрещаем.
     *
     * Это слишком неоднозначный перенос для автоматической пересортировки.
     */
    private val preventSameLeafCrossBranchMove: Boolean = true,
    /**
     * Маленький штраф за сам факт переноса файла.
     */
    private val movePenalty: Double = 0.02,

    /**
     * Дополнительный маленький штраф за переход в другую ветку.
     */
    private val crossBranchPenalty: Double = 0.08,

    /**
     * Защита от погрешностей Double.
     */
    private val epsilon: Double = 0.000001,
    /**
     * Минимальный score лучшей папки.
     * Защищает от переносов на score=0.0 или около того.
     */
    private val minBestScore: Double = 0.5,

    /**
     * Минимальный разрыв между 1-м и 2-м кандидатом.
     * Защищает от спорных случаев типа:
     * puppy/Max:1.72 и dog/puppy:1.72.
     */
    private val minConfidenceGap: Double = 0.15,
    /**
     * Для перехода между ветками требуем больший разрыв.
     */
    private val crossBranchMinConfidenceGap: Double = 0.25,
    /**
     * Минимальная выгода переноса.
     */
    private val minMoveGain: Double = 0.15,
    /**
     * Для перехода между ветками требуем большую выгоду.
     */
    private val crossBranchMinMoveGain: Double = 0.35,
) {
    sealed interface Decision {
        data object Skip : Decision

        data class Suggest(
            val suggestion: ResortSuggestionV2
        ) : Decision
    }

    private data class PolicyCandidate(
        val candidate: AdjustedCandidateScore,
        val branch: String?,
        val penalty: Double,
        val effectiveScore: Double,
        val isCurrentFolder: Boolean,
        val isCrossBranch: Boolean,
        val sameBranch: Boolean
    )

    fun decide(
        file: OrganizableFile,
        currentPath: String,
        evaluation: CoreEvaluationResult
    ): Decision {
        fun skip(reason: String): Decision {
            Log.d(
                "ResortContour",
                "Skip file=${file.name}, reason=$reason"
            )
            return Decision.Skip
        }

        if (file.pinned == true) {
            return skip("pinned")
        }

        if (evaluation.rankedCandidates.isEmpty()) {
            return skip("no ranked candidates")
        }

        val confidenceGap = evaluation.decision.confidenceGap
        val fileIsInRoot = file.classificationFolderId.isNullOrBlank()

        /**
         * Текущая папка файла среди заново оценённых кандидатов.
         */
        val currentCandidate = evaluation.rankedCandidates
            .firstOrNull { candidate ->
                candidate.folder.id == file.classificationFolderId
            }

        /**
         * Score текущего места.
         *
         * Если файл в корне, считаем currentScore = 0.0,
         * потому что корень не является папкой-кандидатом.
         */
        val currentScore = when {
            fileIsInRoot -> 0.0
            currentCandidate != null -> currentCandidate.finalScore
            file.classificationScore != null -> file.classificationScore
            else -> 0.0
        }

        /**
         * Фактическая текущая ветка.
         *
         * Лучше брать её из currentCandidate, потому что это текущее место файла.
         * classificationBranch используем только как fallback.
         */
        val currentBranch = currentCandidate?.folder?.path
            ?.let { branchNameOf(it) }
            ?: file.classificationBranch

        /**
         * Считаем effectiveScore для всех кандидатов:
         *
         * effectiveScore = rawScore - penalty
         *
         * И только после этого выбираем лучшего.
         */
        val policyCandidates = evaluation.rankedCandidates
            .map { candidate ->
                val candidateBranch = branchNameOf(candidate.folder.path)
                val isCurrentFolder = candidate.folder.id == file.classificationFolderId
                val sameBranch = currentBranch != null && currentBranch == candidateBranch

                val isCrossBranch =
                    !fileIsInRoot &&
                            !isCurrentFolder &&
                            currentBranch != null &&
                            candidateBranch != null &&
                            !sameBranch

                val penalty = when {
                    fileIsInRoot -> 0.0
                    isCurrentFolder -> 0.0
                    isCrossBranch -> movePenalty + crossBranchPenalty
                    else -> movePenalty
                }

                PolicyCandidate(
                    candidate = candidate,
                    branch = candidateBranch,
                    penalty = penalty,
                    effectiveScore = candidate.finalScore - penalty,
                    isCurrentFolder = isCurrentFolder,
                    isCrossBranch = isCrossBranch,
                    sameBranch = sameBranch
                )
            }
            .sortedByDescending { it.effectiveScore }

        val bestPolicyCandidate = policyCandidates.firstOrNull()
            ?: return skip("no policy candidates")

        val policyConfidenceGap = if (policyCandidates.size > 1) {
            policyCandidates[0].effectiveScore - policyCandidates[1].effectiveScore
        } else {
            Double.MAX_VALUE
        }

        val best = bestPolicyCandidate.candidate
        val targetBranch = bestPolicyCandidate.branch
        val sameBranch = bestPolicyCandidate.sameBranch
        val moveGain = best.finalScore - currentScore

        if (
            !fileIsInRoot &&
            preventSameLeafCrossBranchMove &&
            !bestPolicyCandidate.isCurrentFolder &&
            isSameLeafCrossBranchBroadMove(
                currentPath = currentPath,
                targetPath = best.folder.path
            )
        ) {
            return skip(
                "ambiguous same-leaf cross-branch broad move: " +
                        "currentPath=$currentPath, " +
                        "targetPath=${best.folder.path}, " +
                        "currentScore=$currentScore, " +
                        "targetScore=${best.finalScore}, " +
                        "moveGain=$moveGain"
            )
        }

        if (
            !fileIsInRoot &&
            bestPolicyCandidate.isCrossBranch &&
            moveGain < minCrossBranchMoveGain
        ) {
            return skip(
                "cross-branch move gain is too small: " +
                        "currentPath=$currentPath, " +
                        "targetPath=${best.folder.path}, " +
                        "currentScore=$currentScore, " +
                        "targetScore=${best.finalScore}, " +
                        "moveGain=$moveGain, " +
                        "requiredGain=$minCrossBranchMoveGain"
            )
        }


        val requiredGap = if (bestPolicyCandidate.isCrossBranch) {
            crossBranchMinConfidenceGap
        } else {
            minConfidenceGap
        }

        val requiredMoveGain = if (bestPolicyCandidate.isCrossBranch) {
            crossBranchMinMoveGain
        } else {
            minMoveGain
        }

        Log.d(
            "ResortContour",
            "Policy best file=${file.name}, currentPath=$currentPath, " +
                    "best=${best.folder.path}, rawScore=${best.finalScore}, " +
                    "currentScore=$currentScore, penalty=${bestPolicyCandidate.penalty}, " +
                    "effectiveScore=${bestPolicyCandidate.effectiveScore}, " +
                    "currentBranch=$currentBranch, targetBranch=$targetBranch, " +
                    "sameBranch=$sameBranch, crossBranch=${bestPolicyCandidate.isCrossBranch}"
        )

        /**
         * Сценарий 1:
         * файл лежит в корне.
         *
         * Здесь не сравниваем с текущей папкой.
         * Просто проверяем, что лучшая папка достаточно хорошая.
         */
        if (fileIsInRoot) {
            if (best.finalScore + epsilon < minBestScore) {
                return skip(
                    "root raw score too small: " +
                            "bestScore=${best.finalScore}, " +
                            "minBestScore=$minBestScore, " +
                            "best=${best.folder.path}"
                )
            }

            if (
                policyCandidates.size > 1 &&
                policyConfidenceGap + epsilon < requiredGap
            ) {
                return skip(
                    "root confidence gap too small: " +
                            "gap=$confidenceGap, " +
                            "minGap=$minConfidenceGap, " +
                            "best=${best.folder.path}"
                )
            }
            if (bestPolicyCandidate.effectiveScore <= currentScore + epsilon) {
                return skip(
                    "root target is not better than root: " +
                            "currentScore=$currentScore, " +
                            "bestScore=${best.finalScore}, " +
                            "effectiveScore=${bestPolicyCandidate.effectiveScore}, " +
                            "best=${best.folder.path}"
                )
            }

            return suggest(
                file = file,
                currentPath = currentPath,
                bestFolderId = best.folder.id,
                bestFolderPath = best.folder.path,
                targetBranch = targetBranch,
                targetScore = best.finalScore,
                confidenceGap = confidenceGap,
                currentScore = currentScore,
                moveGain = moveGain,
                sameBranch = sameBranch,
                penalty = bestPolicyCandidate.penalty,
                effectiveScore = bestPolicyCandidate.effectiveScore,
                isCrossBranch = bestPolicyCandidate.isCrossBranch
            )
        }

        /**
         * Если после штрафов лучшая папка — это текущая папка,
         * перенос не нужен.
         */
        if (bestPolicyCandidate.isCurrentFolder) {
            return skip(
                "best effective candidate is current folder=$currentPath, " +
                        "currentScore=$currentScore, " +
                        "effectiveScore=${bestPolicyCandidate.effectiveScore}"
            )
        }
        if (best.finalScore + epsilon < minBestScore) {
            return skip(
                "raw score too small: " +
                        "bestScore=${best.finalScore}, " +
                        "minBestScore=$minBestScore, " +
                        "best=${best.folder.path}, " +
                        "currentPath=$currentPath"
            )
        }

        if (
            evaluation.rankedCandidates.size > 1 &&
            confidenceGap + epsilon < requiredGap
        ) {
            return skip(
                "confidence gap too small: " +
                        "gap=$confidenceGap, " +
                        "requiredGap=$requiredGap, " +
                        "best=${best.folder.path}, " +
                        "currentPath=$currentPath, " +
                        "crossBranch=${bestPolicyCandidate.isCrossBranch}"
            )
        }

        /**
         * Если новая папка после штрафа не лучше текущей,
         * перенос не предлагаем.
         */
        val effectiveMoveGain = bestPolicyCandidate.effectiveScore - currentScore

        /**
         * Если выгода переноса слишком маленькая,
         * перенос не предлагаем.
         *
         * Это сильнее, чем просто:
         * effectiveScore > currentScore.
         *
         * Например:
         * currentScore = 1.70
         * effectiveScore = 1.72
         *
         * Формально новая папка лучше, но разница слишком маленькая,
         * поэтому файл лучше не трогать.
         */
        if (effectiveMoveGain + epsilon < requiredMoveGain) {
            return skip(
                "move gain too small: " +
                        "effectiveMoveGain=$effectiveMoveGain, " +
                        "requiredMoveGain=$requiredMoveGain, " +
                        "currentScore=$currentScore, " +
                        "bestScore=${best.finalScore}, " +
                        "penalty=${bestPolicyCandidate.penalty}, " +
                        "effectiveTargetScore=${bestPolicyCandidate.effectiveScore}, " +
                        "currentPath=$currentPath, best=${best.folder.path}, " +
                        "currentBranch=$currentBranch, targetBranch=$targetBranch, " +
                        "sameBranch=$sameBranch, crossBranch=${bestPolicyCandidate.isCrossBranch}"
            )
        }

        return suggest(
            file = file,
            currentPath = currentPath,
            bestFolderId = best.folder.id,
            bestFolderPath = best.folder.path,
            targetBranch = targetBranch,
            targetScore = best.finalScore,
            confidenceGap = confidenceGap,
            currentScore = currentScore,
            moveGain = moveGain,
            sameBranch = sameBranch,
            penalty = bestPolicyCandidate.penalty,
            effectiveScore = bestPolicyCandidate.effectiveScore,
            isCrossBranch = bestPolicyCandidate.isCrossBranch
        )
    }

    private fun suggest(
        file: OrganizableFile,
        currentPath: String,
        bestFolderId: String,
        bestFolderPath: String,
        targetBranch: String?,
        targetScore: Double,
        confidenceGap: Double,
        currentScore: Double,
        moveGain: Double,
        sameBranch: Boolean,
        penalty: Double,
        effectiveScore: Double,
        isCrossBranch: Boolean
    ): Decision {
        Log.d(
            "ResortContour",
            "Suggest file=${file.name}, from=$currentPath, to=$bestFolderPath, " +
                    "rawScore=$targetScore, currentScore=$currentScore, " +
                    "moveGain=$moveGain, penalty=$penalty, effectiveScore=$effectiveScore, " +
                    "gap=$confidenceGap, sameBranch=$sameBranch, crossBranch=$isCrossBranch"
        )

        return Decision.Suggest(
            ResortSuggestionV2(
                fileId = file.id,
                fileName = file.name,
                fromFolderId = file.classificationFolderId,
                fromPath = currentPath,
                toFolderId = bestFolderId,
                toPath = bestFolderPath,
                targetBranch = targetBranch,
                targetScore = targetScore,
                confidenceGap = confidenceGap
            )
        )
    }

    private fun branchNameOf(path: String?): String? {
        if (path.isNullOrBlank()) {
            return null
        }

        if (path == "(корень)") {
            return null
        }

        return path
            .split('/')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
    private fun pathPartsOf(path: String?): List<String> {
        if (path.isNullOrBlank()) {
            return emptyList()
        }

        if (path == "(корень)") {
            return emptyList()
        }

        return path
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun leafNameOf(path: String?): String? {
        return pathPartsOf(path).lastOrNull()
    }

    private fun depthOf(path: String?): Int {
        return pathPartsOf(path).size
    }

    private fun isSameLeafCrossBranchBroadMove(
        currentPath: String,
        targetPath: String
    ): Boolean {
        val currentLeaf = leafNameOf(currentPath)
        val targetLeaf = leafNameOf(targetPath)

        if (currentLeaf == null || targetLeaf == null) {
            return false
        }

        val currentBranch = branchNameOf(currentPath)
        val targetBranch = branchNameOf(targetPath)

        val isCrossBranch = currentBranch != null &&
                targetBranch != null &&
                currentBranch != targetBranch

        if (!isCrossBranch) {
            return false
        }

        val sameLeaf = currentLeaf.equals(targetLeaf, ignoreCase = true)
        val targetIsNotDeeper = depthOf(targetPath) <= depthOf(currentPath)

        return sameLeaf && targetIsNotDeeper
    }
}