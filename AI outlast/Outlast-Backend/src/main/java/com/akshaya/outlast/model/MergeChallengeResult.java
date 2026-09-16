package com.akshaya.outlast.model;

import java.util.List;

/**
 * Result of Host ranking and immunity award for an individual merge challenge.
 */
public record MergeChallengeResult(
        List<PlayerRanking> rankings,
        Player immunityWinner,
        Player challengeLoser
) {}
