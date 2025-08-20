package com.test.blabify.domain.impl
//не работает но пока имеет зависимость, которую нужно отключить, а этот файл удалить
import com.test.blabify.domain.api.ClassificationResult
import com.test.blabify.domain.api.FileClassifier

class RuleBasedClassifier : FileClassifier {
    override suspend fun classify(
        fileName: String,
        fileText: String?,
        candidateFolders: List<String>
    ): ClassificationResult {
        val textLower = (fileName + " " + (fileText ?: "")).lowercase()

        val matches = candidateFolders.associateWith { folder ->
            folder.lowercase()
                .split(" ")
                .count { w -> w.isNotBlank() && textLower.contains(w) }
        }

        val best = matches.maxByOrNull { it.value }
        return if (best != null && best.value > 0)
            ClassificationResult(best.key, confidence = 0.6f)
        else
            ClassificationResult(null, confidence = 0f)
    }
}