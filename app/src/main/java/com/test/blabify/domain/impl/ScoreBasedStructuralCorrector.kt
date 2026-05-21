package com.test.blabify.domain.impl

import com.test.blabify.domain.api.AdjustedCandidateScore
import com.test.blabify.domain.api.BaseCandidateScore
import com.test.blabify.domain.api.StructuralCorrector

class ScoreBasedStructuralCorrector(
    /**
     * Если оценка кандидата почти нулевая, структуру не трогаем.
     * Не надо поднимать или штрафовать папки, которые модель сама не выбрала.
     */
    private val minCandidateScoreForCorrection: Double = 0.05,

    /**
     * Допустимая разница между дочерней папкой и родителем.
     *
     * Например:
     * dog/puppy = 1.0
     * dog = 0.83
     *
     * Это нормально, штраф не нужен.
     */
    private val allowedParentGap: Double = 0.20,

    /**
     * Насколько сильно штрафуем дочернюю папку,
     * если она сильно выше родителя.
     */
    private val parentMismatchPenaltyWeight: Double = 0.25,

    /**
     * Максимальный штраф.
     *
     * Он нужен, чтобы структурная корректировка не могла полностью
     * уничтожить решение базового классификатора.
     */
    private val maxPenalty: Double = 0.15
) : StructuralCorrector {

    override suspend fun applyCorrections(
        fileText: String?,
        baseScores: List<BaseCandidateScore>
    ): List<AdjustedCandidateScore> {
        val scoreByPath = baseScores.associate { score ->
            score.folder.path to score.baseScore
        }

        return baseScores
            .map { base ->
                val structuralCorrection = calculateStructuralCorrection(
                    base = base,
                    scoreByPath = scoreByPath
                )

                AdjustedCandidateScore(
                    folder = base.folder,
                    baseScore = base.baseScore,
                    structuralCorrection = structuralCorrection,
                    finalScore = base.baseScore + structuralCorrection
                )
            }
            .sortedByDescending { it.finalScore }
    }

    private fun calculateStructuralCorrection(
        base: BaseCandidateScore,
        scoreByPath: Map<String, Double>
    ): Double {
        val pathParts = base.folder.path
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (pathParts.size <= 1) {
            return 0.0
        }

        val candidateScore = base.baseScore

        if (candidateScore < minCandidateScoreForCorrection) {
            return 0.0
        }

        val parentPaths = (1 until pathParts.size).map { endIndex ->
            pathParts
                .take(endIndex)
                .joinToString("/")
        }

        val parentScores = parentPaths.mapNotNull { parentPath ->
            scoreByPath[parentPath]
        }

        if (parentScores.isEmpty()) {
            return 0.0
        }

        /**
         * Для глубокого пути важен самый слабый подтверждённый родитель.
         *
         * Например:
         * economy/tables/swot
         *
         * Если economy низкий, а tables/swot высокий,
         * значит путь подозрительный.
         */
        val weakestParentScore = parentScores.minOrNull() ?: return 0.0

        val unsupportedGap = candidateScore - weakestParentScore - allowedParentGap

        if (unsupportedGap <= 0.0) {
            return 0.0
        }

        val penalty = unsupportedGap * parentMismatchPenaltyWeight

        return -penalty.coerceAtMost(maxPenalty)
    }
}