package com.akshaya.outlast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;

@ConfigurationProperties(prefix = "game")
public class GameConfig {

    @Value("${llm.provider:groq}")
    private String provider;

    private Host host;
    private Limits limits;
    private List<String> availableModels;
    private List<FallbackChallenge> teamFallbacks;
    private List<FallbackChallenge> mergeFallbacks;

    // Getters returning dynamic values based on provider
    public List<String> availableModels() {
        if (availableModels != null && !availableModels.isEmpty()) {
            return availableModels;
        }
        return switch (provider.toLowerCase()) {
            case "gemini" -> List.of("gemini-3.6-flash", "gemini-3.5-flash-lite");
            case "ollama" -> List.of("llama3");
            default -> List.of("openai/gpt-oss-20b", "openai/gpt-oss-120b");
        };
    }

    public void setAvailableModels(List<String> availableModels) {
        this.availableModels = availableModels;
    }

    public String hostModel() {
        return switch (provider.toLowerCase()) {
            case "gemini" -> "gemini-3.5-flash-lite";
            case "ollama" -> "llama3";
            default -> "openai/gpt-oss-20b";
        };
    }

    public String creatorModel() {
        return switch (provider.toLowerCase()) {
            case "gemini" -> "gemini-3.6-flash";
            case "ollama" -> "llama3";
            default -> "openai/gpt-oss-20b";
        };
    }

    public Host host() {
        if (host == null) {
            return new Host("The Host", "Dramatic Showman", "Charismatic...", hostModel());
        }
        return new Host(host.name(), host.archetype(), host.traits(), hostModel());
    }

    public Limits limits() {
        return limits;
    }

    public List<FallbackChallenge> teamFallbacks() {
        return teamFallbacks;
    }

    public List<FallbackChallenge> mergeFallbacks() {
        return mergeFallbacks;
    }

    // Setters for Spring Boot property binding
    public void setHost(Host host) {
        this.host = host;
    }

    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    public void setTeamFallbacks(List<FallbackChallenge> teamFallbacks) {
        this.teamFallbacks = teamFallbacks;
    }

    public void setMergeFallbacks(List<FallbackChallenge> mergeFallbacks) {
        this.mergeFallbacks = mergeFallbacks;
    }

    // Inner records
    public record FallbackChallenge(
        String title,
        String theme,
        String scenario,
        String objective,
        List<String> materials,
        List<String> rules,
        List<String> rewards
    ) {}

    public record Host(
        String name,
        String archetype,
        String traits,
        String modelId
    ) {}

    public record Limits(
        int maxQuestionWords,
        int maxAnswerWords,
        int maxAnswerSentences,
        int maxReasonSentences,
        int maxWelcomeSentences,
        int maxCampConversations,
        int maxCampMessageWords,
        int maxSecretMessageWords,
        int maxReflectionSummarySentences,
        int maxChallengeIdeaWords,
        int maxChallengeUpgradeWords,
        int maxStrategyMessageWords
    ) {}
}
