package com.test.blabify.domain.api

interface BaseFileClassifier {
    suspend fun scoreCandidates(
        fileName: String,
        fileText: String?,
        candidates: List<CandidateFolder>
    ): List<BaseCandidateScore>
}