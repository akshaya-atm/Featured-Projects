package com.akshaya.outlast.model;

/**
 * A secret 1-on-1 exchange between two players.
 * from sends a private message to 'to', and 'to' replies.
 *
 * If the player chose to stay silent: to=null, message=null, reply=null.
 * Reusable across camp life, merge, tribal council — any phase.
 */
public record SecretMessage(
        Player from,
        Player to,
        String message,
        String reply,
        boolean allianceProposed,
        String trueTarget,
        String backupTarget,
        String trueReasoning,
        boolean isDeceptive
) {}
