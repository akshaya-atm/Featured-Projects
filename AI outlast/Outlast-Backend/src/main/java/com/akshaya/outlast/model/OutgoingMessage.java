package com.akshaya.outlast.model;

/**
 * Intermediate model — a message crafted by one player directed at a specific teammate,
 * before a reply has been received.
 */
public record OutgoingMessage(Player from, Player to, String message) {}
