package com.test.blabify.domain.impl
//не работает но пока имеет зависимость, которую нужно отключить, а этот файл удалить
import com.test.blabify.domain.api.ClassificationResult
import com.test.blabify.domain.api.*




class RuleBasedClassifier : FileClassifier {

    private fun tokenize(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9а-яё]+")).filter { it.length >= 2 }


    override suspend fun classify(
        fileName: String,
        fileText: String?,
        candidates: List<CandidateFolder>
    ): ClassificationResult {
        val text  = (fileName + " " + (fileText ?: "")).lowercase()

        var best: CandidateFolder? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var runnerUp = Double.NEGATIVE_INFINITY

        for (c in candidates) {
            val nameTokens = tokenize(c.name)
            val nameHits = nameTokens.count { t -> text.contains(t) }.toDouble()

            val segments = c.path.split('/').map { it.lowercase() }
            val parents = segments.dropLast(1).asReversed()
            var ancestorsBonus = 0.0
            var w = 0.7
            for (seg in parents) {
                val hits = tokenize(seg).count { t -> text.contains(t) }
                ancestorsBonus += w * hits
                w *= 0.7
            }

            val depthBias = 0.1 * c.level      // маленький приоритет глубине
            val score = nameHits + ancestorsBonus + depthBias

            if (score > bestScore + 1e-9) {
                runnerUp = bestScore
                bestScore = score
                best = c
            } else if (score > runnerUp) {
                runnerUp = score
            }
        }
        if (best == null || bestScore <= 0.0) {
            return ClassificationResult(null, null, 0f, 0.0)
        }

        val margin = (bestScore - runnerUp).coerceAtLeast(0.0)
        val conf = when {
            margin >= 1.0 -> 0.9f
            margin >= 0.5 -> 0.8f
            margin >= 0.3 -> 0.7f
            else -> 0.49f
        }

        return ClassificationResult(best!!.id, best!!.name, conf, bestScore)
    }
}