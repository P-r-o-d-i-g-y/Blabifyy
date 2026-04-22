package com.test.blabify.domain.impl

import com.test.blabify.domain.api.BaseCandidateScore
import com.test.blabify.domain.api.BaseFileClassifier
import com.test.blabify.domain.api.CandidateFolder

class RuleBasedBaseClassifier : BaseFileClassifier {

    private fun tokenize(s: String): List<String> =
        s.lowercase()
            .split(Regex("[^a-z0-9а-яё]+"))
            .filter { it.length >= 2 }

    private fun scoreBase(textLower: String, candidate: CandidateFolder): Double {
        val nameTokens = tokenize(candidate.name)
        val nameHits = nameTokens.count { token -> textLower.contains(token) }.toDouble()

        return nameHits
    }

    override suspend fun scoreCandidates(
        fileName: String,
        fileText: String?,
        candidates: List<CandidateFolder>
    ): List<BaseCandidateScore> {
        val text = (fileName + " " + (fileText ?: "")).lowercase()

        return candidates.map { candidate ->
            BaseCandidateScore(
                folder = candidate,
                baseScore = scoreBase(text, candidate)
            )
        }
    }
}