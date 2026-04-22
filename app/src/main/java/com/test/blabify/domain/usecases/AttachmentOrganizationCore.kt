package com.test.blabify.domain.usecases
/**
 * Общее ядро оценки вложения.
 * Последовательно применяет базовый классификатор, структурную корректировку
 * и формирует итоговое решение для дальнейших контуров применения.
 */


import com.test.blabify.domain.api.BaseFileClassifier
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.CoreDecision
import com.test.blabify.domain.api.CoreEvaluationResult
import com.test.blabify.domain.api.StructuralCorrector

class AttachmentOrganizationCore(
    private val baseClassifier: BaseFileClassifier,
    private val structuralCorrector: StructuralCorrector
) {
    suspend fun evaluate(
        fileName: String,
        fileText: String?,
        candidates: List<CandidateFolder>
    ): CoreEvaluationResult {
        val baseScores = baseClassifier.scoreCandidates(
            fileName = fileName,
            fileText = fileText,
            candidates = candidates
        )

        val adjustedScores = structuralCorrector.applyCorrections(
            fileName = fileName,
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
}