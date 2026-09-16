package com.akshaya.outlast.model;

/**
 * Represents the alliance status between two players, from one player's perspective.
 * Each player independently tracks their own view — statuses can be asymmetric.
 *
 * Example: A thinks CONFIRMED, B thinks NONE (B was just being polite).
 */
public enum AllianceStatus {
    NONE,       // No alliance contact made
    PROPOSED,   // I reached out (one-sided, awaiting response)
    CONFIRMED,  // Both sides acknowledged alliance
    BROKEN      // Was confirmed, then betrayed
}
