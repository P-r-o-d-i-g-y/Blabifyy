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
    private val depthBiasPerLevel: Double = 0.02,
    /**
     * Штраф за токен родительского сегмента, которого нет в тексте.
     *
     * Пример:
     * путь puppy/Max, текст содержит dog и Max, но не puppy.
     * Тогда puppy должен штрафовать этот путь.
     */
    private val missingAncestorTokenPenalty: Double = 0.35,
    /**
     * Дополнительный штраф за ситуацию:
     * leaf совпал, но ни один родитель не подтверждён.
     *
     * Пример:
     * puppy/Max:
     * Max найден, puppy не найден.
     */
    private val leafOnlyDeepPathPenalty: Double = 0.45
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

        if (segments.isEmpty()) {
            return 0.0
        }
        val leaf = segments.last()
        val parents = segments.dropLast(1)

        val leafTokens = tokenize(leaf)
        val parentTokens = parents.flatMap { segment ->
            tokenize(segment)
        }
        val leafHits = leafTokens.count { token ->
            textLower.contains(token)
        }

        val parentHits = parentTokens.count { token ->
            textLower.contains(token)
        }

        val missingParentHits = parentTokens.count { token ->
            !textLower.contains(token)
        }
        /**
         * Старый бонус за родителей.
         *
         * Важно:
         * asReversed() оставляем, чтобы ближайший родитель имел больший вес.
         *
         * Пример:
         * animals/dog/puppy
         * ближайший родитель для puppy — dog.
         */
        val parentsReversed = parents.asReversed()
        var ancestorsBonus = 0.0
        var weight = initialAncestorWeight

        for (segment in parentsReversed) {
            val hits = tokenize(segment).count { token ->
                textLower.contains(token)
            }

            ancestorsBonus += weight * hits
            weight *= ancestorWeightDecay
        }
        /**
         * Глубина сама по себе не должна сильно тянуть кандидата вверх.
         *
         * Если depthBiasPerLevel сейчас большой, лучше уменьшить.
         */
        val depthBias = depthBiasPerLevel * candidate.level
        /**
         * Штраф за неподтверждённых родителей.
         *
         * Пример:
         * candidate = puppy/Max
         * text = dog named Max
         *
         * leafHits = 1, потому что Max найден
         * parentHits = 0, потому что puppy не найден
         * missingParentHits = 1
         *
         * Значит puppy/Max получает штраф.
         */
        val missingAncestorPenalty = missingParentHits * missingAncestorTokenPenalty
        /**
         * Дополнительный штраф:
         * глубокий путь, leaf совпал, но родительский путь вообще не подтверждён.
         *
         * Это защищает от ложных переносов по одному имени:
         * puppy/Max, project/Max, people/Max и т.д.
         */
        val leafOnlyPenalty = if (
            parents.isNotEmpty() &&
            leafHits > 0 &&
            parentHits == 0
        ) {
            leafOnlyDeepPathPenalty
        } else {
            0.0
        }

        return ancestorsBonus + depthBias - missingAncestorPenalty - leafOnlyPenalty
    }

    override suspend fun applyCorrections(
        fileText: String?,
        baseScores: List<BaseCandidateScore>
    ): List<AdjustedCandidateScore> {
        val textLower = fileText.orEmpty().lowercase()

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