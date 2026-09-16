package com.akshaya.outlast.model;

/**
 * A vote cast by a contestant at Tribal Council.
 */
public record Vote(Player voter, Player target, String reason) {}
