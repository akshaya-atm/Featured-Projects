package com.akshaya.outlast.model;

import com.akshaya.outlast.utils.Gender;
import com.akshaya.outlast.utils.PersonalityType;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class Player {
    // Basic Identity (Immutable)
    private final String id;
    private final String name;
    private final String modelId;
    private final Gender gender;
    private final PersonalityType personalityType;

    // Dynamic Game State (Mutable)
    private boolean eliminated;

    // Scalable Stats & Attributes (Initialized by LLM, managed by engine)
    private Map<StatType, Integer> stats = new HashMap<>();

    // Social Memory (Updated after each phase)
    private Map<String, PlayerRelation> relations = new HashMap<>();  // playerId -> relation
    private String gameSummary = "Just arrived. No impressions formed yet.";

    public Player(String id, String name, String modelId, Gender gender, PersonalityType personalityType) {
        this.id = id;
        this.name = name;
        this.modelId = modelId;
        this.gender = gender;
        this.personalityType = personalityType;
        this.eliminated = false;
    }

    // Identity Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getModelId() { return modelId; }
    public Gender getGender() { return gender; }
    public PersonalityType getPersonalityType() { return personalityType; }

    public boolean isEliminated() { return eliminated; }
    public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }

    // Stats
    public Map<StatType, Integer> getStats() { return stats; }
    public void setStats(Map<StatType, Integer> stats) { this.stats = stats; }
    public int getStat(StatType key) { return stats.getOrDefault(key, 0); }
    public void setStat(StatType key, int value) { stats.put(key, value); }

    // Relations
    public Map<String, PlayerRelation> getRelations() { return relations; }

    public PlayerRelation getRelation(String playerId) { return relations.get(playerId); }

    /**
     * Initializes a neutral PlayerRelation (trust=50, no notes) for each teammate.
     * Call this once after teams are formed.
     */
    public void initializeRelations(List<Player> teammates) {
        for (Player teammate : teammates) {
            if (!teammate.getId().equals(this.id)) {
                relations.putIfAbsent(teammate.getId(), new PlayerRelation(teammate));
            }
        }
    }

    public int getTrustScore(String playerId) {
        PlayerRelation rel = relations.get(playerId);
        return rel != null ? rel.getTrustScore() : 50;
    }

    public void setTrustScore(String playerId, int score) {
        PlayerRelation rel = relations.get(playerId);
        if (rel != null) rel.setTrustScore(score);
    }

    /**
     * Returns a formatted trust summary for use in LLM prompts, excluding eliminated players.
     * Example:
     *   Riya (contestant_3): Trust 65 — Was evasive during camp talk
     *   Jake (contestant_7): Trust 30 — Suspicious, avoided eye contact
     */
    public String getRelationsSummary() {
        if (relations.isEmpty()) return "No teammates known yet.";
        return relations.values().stream()
                .filter(r -> !r.isTargetEliminated()) // Do not show eliminated contestants
                .map(PlayerRelation::toPromptString)
                .collect(Collectors.joining("\n"));
    }

    // Game Summary
    public String getGameSummary() { return gameSummary; }
    public void setGameSummary(String gameSummary) { this.gameSummary = gameSummary; }

    // Introduction
    private String introduction;
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }

    // System Prompt
    private String systemPrompt;
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    // Individual Challenge Wins
    private int individualWins = 0;
    public int getIndividualWins() { return individualWins; }
    public void setIndividualWins(int wins) { this.individualWins = wins; }
    public void incrementIndividualWins() { this.individualWins++; }

    // Message History Log
    private final List<String> messageLog = new java.util.ArrayList<>();
    public void logMessage(String logEntry) { this.messageLog.add(logEntry); }
    public List<String> getMessageLog() { return messageLog; }
    public void clearMessageLog() { this.messageLog.clear(); }
    public String getMessageLogSummary() {
        if (messageLog.isEmpty()) return "No messages sent or received yet.";
        return String.join("\n", messageLog);
    }

    // Voting Target Cache
    private String trueTarget;
    private String backupTarget;
    private String trueReasoning;
    public String getTrueTarget() { return trueTarget; }
    public void setTrueTarget(String target) { this.trueTarget = target; }
    public String getBackupTarget() { return backupTarget; }
    public void setBackupTarget(String target) { this.backupTarget = target; }
    public String getTrueReasoning() { return trueReasoning; }
    public void setTrueReasoning(String reasoning) { this.trueReasoning = reasoning; }
}
