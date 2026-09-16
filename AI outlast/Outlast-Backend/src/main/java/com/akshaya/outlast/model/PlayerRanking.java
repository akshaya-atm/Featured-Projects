package com.akshaya.outlast.model;

/**
 * A player's performance ranking in an individual challenge.
 */
public record PlayerRanking(Player player, int rank, String reasoning) {}
