package com.test.blabify.domain.usecases
/**
 * Общее ядро оценки вложения.
 * Последовательно применяет базовый классификатор, структурную корректировку
 * и формирует итоговое решение для дальнейших контуров применения.
 */

import com.test.blabify.domain.api.BaseCandidateScore
import com.test.blabify.domain.api.BaseFileClassifier
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.CoreDecision
import com.test.blabify.domain.api.CoreEvaluationResult
import com.test.blabify.domain.api.StructuralCorrector
import kotlin.math.abs
import kotlin.math.pow

class AttachmentOrganizationCore(
    private val baseClassifier: BaseFileClassifier,
    private val structuralCorrector: StructuralCorrector
) {
    suspend fun evaluate(
        fileText: String?,
        candidates: List<CandidateFolder>
    ): CoreEvaluationResult {
        val rawBaseScores  = baseClassifier.scoreCandidates(
            fileText = fileText,
            candidates = candidates
        )
        android.util.Log.d(
            "AttachmentCore",
            "Raw base scores: " + rawBaseScores.joinToString { score ->
                "${score.folder.path}:${score.baseScore}"
            }
        )

        val baseScores = normalizeBaseScores(rawBaseScores)
        android.util.Log.d(
            "AttachmentCore",
            "Normalized base scores: " + baseScores.joinToString { score ->
                "${score.folder.path}:${score.baseScore}"
            }
        )
        val adjustedScores = structuralCorrector.applyCorrections(
            fileText = fileText,
            baseScores = baseScores
        ).sortedByDescending { it.finalScore }

        val best = adjustedScores.getOrNull(0)
        val secondBest = adjustedScores.getOrNull(1)

        val confidenceGap = when {
            best == null -> 0.0
            secondBest == null -> best.finalScore
            else -> best.finalScore - secondBest.finalScore
        }

        return CoreEvaluationResult(
            rankedCandidates = adjustedScores,
            decision = CoreDecision(
                best = best,
                secondBest = secondBest,
                confidenceGap = confidenceGap
            )
        )
    }
    /**
     * Приводит оценки любого базового классификатора к единой шкале 0..1.
     *
     * Зачем:
     * - rule-based может возвращать 0, 1, 2, 3...
     * - embedding обычно возвращает близкие значения вроде 0.76, 0.79, 0.81
     *
     * После нормализации структурная корректировка работает с единой шкалой,
     * а не с сырыми значениями конкретной реализации классификатора.
     */
    private fun normalizeBaseScores(
        scores: List<BaseCandidateScore>
    ): List<BaseCandidateScore> {
        if (scores.isEmpty()) {
            return scores
        }

        val values = scores.map { it.baseScore }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val spread = max - min

        val epsilon = 0.000001
        val scale = maxOf(abs(max), abs(min), epsilon)
        val relativeSpread = spread / scale

        /**
         * Если классификатор всем дал почти одинаковую оценку,
         * значит он не смог нормально различить кандидатов.
         */
        val minRelativeSpread = 0.005

        if (spread <= epsilon || relativeSpread < minRelativeSpread) {
            return scores.map { score ->
                score.copy(baseScore = 0.0)
            }
        }

        /**
         * power можно оставить 1.0.
         * чтобы сильнее выделять лучшего кандидата,
         * можно  1.2 или 1.5.
         */
        val power = 1.0

        return scores.map { score ->
            val normalizedScore = ((score.baseScore - min) / spread)
                .coerceIn(0.0, 1.0)
                .pow(power)

            score.copy(baseScore = normalizedScore)
        }
    }
}