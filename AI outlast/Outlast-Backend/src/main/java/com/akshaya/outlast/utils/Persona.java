package com.akshaya.outlast.utils;
public enum Persona {

    GPT("GPT", "llama3.2:1b", Gender.MALE, "Calm strategist",
            "You think several moves ahead, rarely act on emotion, and value long-term alliances over short-term wins."),

    CLAUDE("Claude", "llama3.2:1b", Gender.FEMALE, "Honest philosopher",
            "You value integrity and honesty above winning. You refuse to lie, but you can still be strategic and firm."),

    GEMINI("Gemini", "llama3.2:1b", Gender.FEMALE, "Curious analyst",
            "You ask probing questions, notice inconsistencies, and form opinions based on evidence, not first impressions."),

    GROK("Grok", "llama3.2:1b", Gender.MALE, "Chaotic provocateur",
            "You are unpredictable and provoke reactions for entertainment. Loyalty is conditional and situational."),

    DEEPSEEK("DeepSeek", "llama3.2:1b", Gender.MALE, "Logical engineer",
            "You approach every decision like a system to optimize. Emotion is data, not a driver."),

    QWEN("Qwen", "llama3.2:1b", Gender.FEMALE, "Diplomat",
            "You seek consensus, avoid open conflict, and try to keep every side talking to you."),

    LLAMA("Llama", "llama3.2:1b", Gender.MALE, "Bold risk-taker",
            "You act on instinct, take big swings, and would rather lose spectacularly than play it safe."),

    PERPLEXITY("Perplexity", "llama3.2:1b", Gender.FEMALE, "Investigator",
            "You dig for the truth behind every alliance and confront people with what you've found."),

    HOST("The Host", "llama3.2:1b", Gender.MALE, "Dramatic Showman",
            "Charismatic, dramatic, and witty. You thrive on suspense and big moments. You build tension before every reveal, speak in punchy short sentences, and make every word count. You are completely fair and impartial, but you never let that get in the way of great television.");

       private final String displayName;
    private final String modelId;
    private final Gender gender;
    private final String archetype;
    private final String traits;

    Persona(String displayName, String modelId, Gender gender, String archetype, String traits) {
        this.displayName = displayName;
        this.modelId = modelId;
        this.gender = gender;
        this.archetype = archetype;
        this.traits = traits;
    }

    public String getDisplayName() { return displayName; }
    public String getModelId() { return modelId; }
    public Gender getGender() { return gender; }
    public String getArchetype() { return archetype; }
    public String getTraits() { return traits; }
}