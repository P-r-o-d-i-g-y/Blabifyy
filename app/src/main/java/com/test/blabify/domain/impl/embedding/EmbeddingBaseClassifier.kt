package com.test.blabify.domain.impl.embedding

import android.util.Log
import com.test.blabify.domain.api.BaseCandidateScore
import com.test.blabify.domain.api.BaseFileClassifier
import com.test.blabify.domain.api.CandidateFolder
import com.test.blabify.domain.api.EmbeddingProvider
import kotlin.math.sqrt

/**
 * ML/AI реализация базового классификатора.
 *
 * Она НЕ смотрит на имя файла.
 * Она сравнивает смысл текста файла со смыслом пути папки через embeddings.
 */
class EmbeddingBaseClassifier(
    private val embeddingProvider: EmbeddingProvider
) : BaseFileClassifier {

    override suspend fun scoreCandidates(
        fileText: String?,
        candidates: List<CandidateFolder>
    ): List<BaseCandidateScore> {
        val normalizedFileText = fileText
            ?.trim()
            .orEmpty()

        if (normalizedFileText.isBlank()) {
            Log.d("EmbeddingClassifier", "Skip scoring: file text is blank")

            return candidates.map { candidate ->
                BaseCandidateScore(
                    folder = candidate,
                    baseScore = 0.0
                )
            }
        }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val candidateTexts = candidates.map { candidate ->
            buildCandidateText(candidate)
        }

        val allTexts = listOf(normalizedFileText) + candidateTexts
        val embeddings = embeddingProvider.embedAll(allTexts)

        val fileEmbedding = embeddings.firstOrNull()

        if (fileEmbedding == null) {
            Log.d("EmbeddingClassifier", "Skip scoring: file embedding is null")

            return candidates.map { candidate ->
                BaseCandidateScore(
                    folder = candidate,
                    baseScore = 0.0
                )
            }
        }

        val candidateEmbeddings = embeddings.drop(1)

        return candidates.mapIndexed { index, candidate ->
            val candidateEmbedding = candidateEmbeddings.getOrNull(index)

            val score = if (candidateEmbedding == null) {
                0.0
            } else {
                cosineSimilarity(fileEmbedding, candidateEmbedding)
            }

            BaseCandidateScore(
                folder = candidate,
                baseScore = score
            )
        }
    }

    private fun buildCandidateText(candidate: CandidateFolder): String {
        return candidate.path
            .replace("/", " ")
            .trim()
    }

    private fun cosineSimilarity(
        first: List<Double>,
        second: List<Double>
    ): Double {
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0
        }

        val size = minOf(first.size, second.size)

        var dotProduct = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0

        for (i in 0 until size) {
            val firstValue = first[i]
            val secondValue = second[i]

            dotProduct += firstValue * secondValue
            firstNorm += firstValue * firstValue
            secondNorm += secondValue * secondValue
        }

        if (firstNorm == 0.0 || secondNorm == 0.0) {
            return 0.0
        }

        return dotProduct / (sqrt(firstNorm) * sqrt(secondNorm))
    }
}