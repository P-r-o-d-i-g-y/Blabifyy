package com.test.blabify.domain.api
/**
 * Контракт слоя структурной корректировки.
 * Применяет поправки к базовым оценкам с учетом положения папки в иерархии.
 */

interface StructuralCorrector {
    suspend fun applyCorrections(
        fileName: String,
        fileText: String?,
        baseScores: List<BaseCandidateScore>
    ): List<AdjustedCandidateScore>
}