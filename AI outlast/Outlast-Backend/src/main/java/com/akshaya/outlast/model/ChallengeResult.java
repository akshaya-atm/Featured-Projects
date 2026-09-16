package com.akshaya.outlast.model;

/**
 * Result of Host judging for a challenge.
 */
public record ChallengeResult(String winningTeamName, String losingTeamName, String reasoning) {}
