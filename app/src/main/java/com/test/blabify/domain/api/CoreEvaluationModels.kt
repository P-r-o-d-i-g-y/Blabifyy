package com.test.blabify.domain.api

data class BaseCandidateScore(
    val folder: CandidateFolder,
    val baseScore: Double
)

data class AdjustedCandidateScore(
    val folder: CandidateFolder,
    val baseScore: Double,
    val structuralCorrection: Double,
    val finalScore: Double
)

data class CoreDecision(
    val best: AdjustedCandidateScore?,
    val secondBest: AdjustedCandidateScore?,
    val confidenceGap: Double
)

data class CoreEvaluationResult(
    val rankedCandidates: List<AdjustedCandidateScore>,
    val decision: CoreDecision
)