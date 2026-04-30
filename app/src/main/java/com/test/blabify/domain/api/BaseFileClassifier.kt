package com.test.blabify.domain.api
/**
 * Контракт базового классификатора.
 * Вычисляет только содержательную оценку кандидатов без структурной корректировки.
 */

interface BaseFileClassifier {
    suspend fun scoreCandidates(
        fileText: String?,
        candidates: List<CandidateFolder>
    ): List<BaseCandidateScore>
}