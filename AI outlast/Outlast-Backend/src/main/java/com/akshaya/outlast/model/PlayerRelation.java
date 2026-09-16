package com.akshaya.outlast.model;

/**
 * Represents this player's relationship with one specific teammate.
 * Stored in Player.relations keyed by the other player's ID.
 */
public class PlayerRelation {

    private final Player targetPlayer;   // teammate reference to check active state dynamically
    private int trustScore;              // 0-100, default 50 (neutral)
    private String notes;                // brief per-person impressions, updated each phase
    private AllianceStatus alliance;     // this player's own view of the alliance

    public PlayerRelation(Player targetPlayer) {
        this.targetPlayer = targetPlayer;
        this.trustScore = 50;
        this.notes = "";
        this.alliance = AllianceStatus.NONE;
    }

    // Getters
    public String getId() { return targetPlayer.getId(); }
    public String getName() { return targetPlayer.getName(); }
    public boolean isTargetEliminated() { return targetPlayer.isEliminated(); }
    public int getTrustScore() { return trustScore; }
    public String getNotes() { return notes; }
    public AllianceStatus getAlliance() { return alliance; }

    // Setters
    public void setTrustScore(int score) { this.trustScore = Math.max(0, Math.min(100, score)); }
    public void setNotes(String notes) { this.notes = notes; }
    public void setAlliance(AllianceStatus alliance) { this.alliance = alliance; }

    /**
     * Returns a natural prompt-friendly summary of this relation.
     * Example: "Riya (contestant_3): Trust 65 | Alliance: PROPOSED — Was evasive during camp talk"
     */
    public String toPromptString() {
        String base = getName() + " (" + getId() + "): Trust " + trustScore + " | Alliance: " + alliance;
        return notes.isBlank() ? base : base + " — " + notes;
    }
}
