package com.test.blabify.domain.usecases
//Мозг — по тексту говорит, в какую папку


class FileClassifier(private val folderNames: List<String>) {

    fun classify(text: String): String? {
        val textLower = text.lowercase()

        val matches = folderNames.associateWith { folder ->
            folder.lowercase().split(" ").count { word ->
                textLower.contains(word)
            }
        }

        val bestMatch = matches.maxByOrNull { it.value }
        return bestMatch?.takeIf { it.value > 0 }?.key
    }
}