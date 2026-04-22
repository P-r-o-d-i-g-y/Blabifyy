package com.test.blabify.domain.impl
/**
 * Базовая реализация структурной корректировки.
 * Добавляет к базовой оценке поправки по предкам папки и глубине в иерархии.
 */

import com.test.blabify.domain.api.AdjustedCandidateScore
import com.test.blabify.domain.api.BaseCandidateScore
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.StructuralCorrector

class DefaultStructuralCorrector(
    private val initialAncestorWeight: Double = 0.7,
    private val ancestorWeightDecay: Double = 0.7,
    private val depthBiasPerLevel: Double = 0.1
) : StructuralCorrector {

    private fun tokenize(s: String): List<String> =
        s.lowercase()
            .split(Regex("[^a-z0-9а-яё]+"))
            .filter { it.length >= 2 }

    private fun calculateStructuralCorrection(
        textLower: String,
        candidate: CandidateFolder
    ): Double {
        val segments = candidate.path
            .split('/')
            .map { it.lowercase() }
            .filter { it.isNotBlank() }

        val parents = segments.dropLast(1).asReversed()

        var ancestorsBonus = 0.0
        var weight = initialAncestorWeight

        for (segment in parents) {
            val hits = tokenize(segment).count { token ->
                textLower.contains(token)
            }

            ancestorsBonus += weight * hits
            weight *= ancestorWeightDecay
        }

        val depthBias = depthBiasPerLevel * candidate.level

        return ancestorsBonus + depthBias
    }

    override suspend fun applyCorrections(
        fileName: String,
        fileText: String?,
        baseScores: List<BaseCandidateScore>
    ): List<AdjustedCandidateScore> {
        val textLower = (fileName + " " + (fileText ?: "")).lowercase()

        return baseScores
            .map { base ->
                val structuralCorrection = calculateStructuralCorrection(
                    textLower = textLower,
                    candidate = base.folder
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
}