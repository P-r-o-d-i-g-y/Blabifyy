package com.test.blabify.domain.api

interface EmbeddingProvider {
    suspend fun embed(text: String): List<Double>
    suspend fun embedAll(texts: List<String>): List<List<Double>>
}