package com.akshaya.outlast.utils;

public enum PersonalityType {
    CALM_STRATEGIST("Calm strategist", "You think several moves ahead, rarely act on emotion, and value long-term alliances over short-term wins."),
    HONEST_PHILOSOPHER("Honest philosopher", "You value integrity and honesty above winning. You refuse to lie, but you can still be strategic and firm."),
    CURIOUS_ANALYST("Curious analyst", "You ask probing questions, notice inconsistencies, and form opinions based on evidence, not first impressions."),
    CHAOTIC_PROVOCATEUR("Chaotic provocateur", "You are unpredictable and provoke reactions for entertainment. Loyalty is conditional and situational."),
    LOGICAL_ENGINEER("Logical engineer", "You approach every decision like a system to optimize. Emotion is data, not a driver."),
    DIPLOMAT("Diplomat", "You seek consensus, avoid open conflict, and try to keep every side talking to you."),
    BOLD_RISK_TAKER("Bold risk-taker", "You act on instinct, take big swings, and would rather lose spectacularly than play it safe."),
    INVESTIGATOR("Investigator", "You dig for the truth behind every alliance and confront people with what you've found."),
    HOST("Host", "A charismatic, fair, witty, and intelligent moderator who thrives on suspense, values strategic thinking, and ensures every challenge is judged with complete impartiality.");

    private final String archetype;
    private final String traits;

    PersonalityType(String archetype, String traits) {
        this.archetype = archetype;
        this.traits = traits;
    }

    public String getArchetype() { return archetype; }
    public String getTraits() { return traits; }
}
