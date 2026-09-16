package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.StatType;
import com.akshaya.outlast.utils.Gender;
import com.akshaya.outlast.utils.PersonalityType;
import com.akshaya.outlast.utils.PromptMessage;
import com.akshaya.outlast.utils.JsonUtils;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.prompt.SystemPromptBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlayerFactory {

    private final LLMClient llmClient;
    private final GameConfig gameConfig;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    public PlayerFactory(LLMClient llmClient, GameConfig gameConfig, SystemPromptBuilder systemPromptBuilder) {
        this.llmClient = llmClient;
        this.gameConfig = gameConfig;
        this.systemPromptBuilder = systemPromptBuilder;
    }

    public Player createPlayer(String id, String name, String modelId, Gender gender, PersonalityType personalityType) {
        Player player = new Player(id, name, modelId, gender, personalityType);
        
        // Generate and set system prompt first (so player can respond in-character)
        player.setSystemPrompt(systemPromptBuilder.build(player));

        // Query the LLM to get personality-based stats AND starting introduction in a single call
        initializePlayerProfile(player);
        
        return player;
    }

    private void initializePlayerProfile(Player player) {
        String userPrompt = """
                You are about to start a game of Survivor. 
                Evaluate your personality archetype and traits:
                - Archetype: %s
                - Traits: %s
                
                You must perform two tasks:
                1. Assign numeric scores (integers between 1 and 100) for each of these survival and game stats:
                   - TRUSTWORTHINESS
                   - SOCIAL_INTELLIGENCE
                   - SURVIVAL_GRIT
                   - COOPERATION
                   - EMOTIONAL_STABILITY
                   - ASSERTIVENESS
                   Assign scores that realistically fit your personality archetype and traits.
                
                2. Write a compelling, in-character self-marketing introduction (15 WORDS MAX — a hard ceiling, not a target. Shorter is better; only use words that earn their place) that you will use to pitch yourself to team captains. 
                   Explain your background, strengths, and what value you bring to a team so they want to draft you. Avoid dry or generic statements. Be as brief as possible.
                
                You MUST respond with ONLY a valid JSON object matching this schema (do NOT include any thoughts, preambles, greetings, or markdown like ```json):
                {
                  "introduction": "your charismatic self-marketing introduction",
                  "stats": {
                    "TRUSTWORTHINESS": 75,
                    "SOCIAL_INTELLIGENCE": 75,
                    "SURVIVAL_GRIT": 75,
                    "COOPERATION": 75,
                    "EMOTIONAL_STABILITY": 75,
                    "ASSERTIVENESS": 75
                  }
                }
                """.formatted(player.getPersonalityType().getArchetype(), player.getPersonalityType().getTraits());

        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", player.getSystemPrompt()));
        messages.add(new PromptMessage("user", userPrompt));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String rawResponse = stripThinking(llmClient.chat(player.getModelId(), messages));
                String cleaned = JsonUtils.extractJson(rawResponse);
                JsonNode node = objectMapper.readTree(cleaned);

                // 1. Set introduction
                if (node.has("introduction")) {
                    player.setIntroduction(node.get("introduction").asText().trim());
                } else {
                    player.setIntroduction("Hi, I am " + player.getName() + ". I am a " + player.getPersonalityType().getArchetype() + " ready to help my team.");
                }

                // 2. Set stats
                Map<StatType, Integer> stats = new HashMap<>();
                if (node.has("stats")) {
                    JsonNode statsNode = node.get("stats");
                    for (StatType type : StatType.values()) {
                        JsonNode valNode = statsNode.get(type.name());
                        stats.put(type, valNode != null ? valNode.asInt() : 75);
                    }
                } else {
                    for (StatType type : StatType.values()) {
                        stats.put(type, 75);
                    }
                }
                player.setStats(stats);
                return; // Success!
            } catch (Exception e) {
                System.out.println("[WARN] Attempt " + attempt + " failed to initialize player profile for " + player.getName() + ": " + e.getMessage());
            }
        }

        // Fallback if all attempts fail
        System.out.println("[WARN] Using hardcoded fallback values for " + player.getName());
        player.setIntroduction("Hi, I'm " + player.getName() + ". I'm ready to work hard for the team and survive the elements.");
        Map<StatType, Integer> fallbackStats = new HashMap<>();
        for (StatType type : StatType.values()) {
            fallbackStats.put(type, 75);
        }
        player.setStats(fallbackStats);
    }

    private String stripThinking(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
