package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.model.*;
import com.akshaya.outlast.utils.PromptMessage;
import com.akshaya.outlast.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ChallengePhase {

    private final LLMClient llmClient;
    private final GameConfig gameConfig;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    public ChallengePhase(LLMClient llmClient, GameConfig gameConfig) {
        this.llmClient = llmClient;
        this.gameConfig = gameConfig;
    }

    public TeamChallengeResult runTeamChallengeFlow(Team team, Challenge challenge) {
        System.out.println("\n>>> Brainstorming Strategy for Team: " + team.getTeamName());

        // Step 1: Pitch initial ideas (1 call per player)
        List<ChallengeIdea> pitches = pitchIdeas(team, challenge);

        // Step 2: Captain reviews all pitches and writes the final team plan (1 call total)
        UpgradedIdea finalPlan = captainUpgradePlan(team, challenge, pitches);

        return new TeamChallengeResult(finalPlan, pitches, List.of(finalPlan), Collections.emptyMap());
    }

    private UpgradedIdea captainUpgradePlan(Team team, Challenge challenge, List<ChallengeIdea> pitches) {
        Player captain = team.getCaptain();
        String teammateList = formatTeammates(team.getPlayers());

        // Format all pitches as context
        StringBuilder pitchesContext = new StringBuilder();
        for (ChallengeIdea pitch : pitches) {
            pitchesContext.append("- ").append(pitch.player().getName()).append(": \"")
                    .append(pitch.idea()).append("\"\n");
        }

        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", captain.getSystemPrompt()));
        messages.add(new PromptMessage("user", """
                Current Phase: Team Brainstorming - Captain Decides Plan
                
                You are the Captain of your team.
                
                Challenge Details:
                - Title: %s
                - Objective: %s
                - Resources: %s
                
                Your Teammates:
                %s
                
                Pitched Strategy Ideas from your teammates:
                %s
                
                Your current game summary:
                %s
                
                Your current social read on teammates:
                %s
                
                As Captain, review all pitches and write the final, upgraded consolidated strategy plan that your team will submit.
                You can combine teammate ideas, select the best one, or propose your own twist.
                Stay fully in character. Max %d words.
                
                Respond with ONLY valid JSON:
                { "plan": "your final consolidated team plan here" }
                """.formatted(
                challenge.title(), challenge.objective(), String.join(", ", challenge.resources()),
                teammateList, pitchesContext.toString(), captain.getGameSummary(), captain.getRelationsSummary(),
                gameConfig.limits().maxChallengeUpgradeWords()
        )));

        String rawResponse = stripThinking(llmClient.chat(captain.getModelId(), messages));
        String plan = parseJsonField(rawResponse, "plan", captain.getName());
        UpgradedIdea finalPlan = new UpgradedIdea(captain, plan);
        System.out.println("[CAPTAIN PLAN] Captain " + captain.getName() + " created final plan: \"" + plan + "\"");
        return finalPlan;
    }

    /**
     * Runs the strategy pitch round for the individual Merge Phase.
     * Each player writes their own strategy pitch for the challenge.
     */
    public Map<String, String> runIndividualChallenge(List<Player> players, Challenge challenge) {
        System.out.println("\n>>> Individual Brainstorming for Merged Tribe");
        Map<String, String> pitches = new HashMap<>();

        for (Player player : players) {
            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", player.getSystemPrompt()));
            messages.add(new PromptMessage("user", """
                    Current Phase: Individual Immunity Challenge - Strategy Pitch
                    
                    Challenge Details:
                    - Theme: %s
                    - Title: %s
                    - Scenario: %s
                    - Objective: %s
                    - Resources: %s
                    - Constraints: %s
                    - Success Criteria: %s
                    
                    Your current game summary:
                    %s
                    
                    Your current social read on other players:
                    %s
                    
                    Propose your individual plan to win this challenge.
                    Stay fully in character. Reflect your personality, archetype, and communication style.
                    Max %d words.
                    
                    Respond with ONLY valid JSON:
                    { "plan": "your individual challenge strategy here" }
                    """.formatted(
                    challenge.theme(), challenge.title(), challenge.scenario(), challenge.objective(),
                    String.join(", ", challenge.resources()), String.join(", ", challenge.constraints()),
                    String.join(", ", challenge.successCriteria()),
                    player.getGameSummary(), player.getRelationsSummary(),
                    gameConfig.limits().maxChallengeIdeaWords()
            )));

            String rawResponse = stripThinking(llmClient.chat(player.getModelId(), messages));
            String plan = parseJsonField(rawResponse, "plan", player.getName());
            pitches.put(player.getId(), plan);
            System.out.println("[PLAN PITCH] " + player.getName() + ": \"" + plan + "\"");
        }
        return pitches;
    }

    private List<ChallengeIdea> pitchIdeas(Team team, Challenge challenge) {
        List<ChallengeIdea> pitches = new ArrayList<>();
        String teammateList = formatTeammates(team.getPlayers());

        for (Player player : team.getPlayers()) {
            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", player.getSystemPrompt()));
            messages.add(new PromptMessage("user", """
                    Current Phase: Team Brainstorming - Pitching Ideas
                    
                    Challenge Details:
                    - Theme: %s
                    - Title: %s
                    - Scenario: %s
                    - Objective: %s
                    - Resources: %s
                    - Constraints: %s
                    - Success Criteria: %s
                    
                    Your Teammates:
                    %s
                    
                    Your current game summary:
                    %s
                    
                    Your current social read on teammates:
                    %s
                    
                    Pitch a 1-2 sentence strategy idea to help your team tackle this challenge.
                    Stay fully in character. Max %d words.
                    
                    Respond with ONLY valid JSON:
                    { "idea": "your pitched strategy here" }
                    """.formatted(
                    challenge.theme(), challenge.title(), challenge.scenario(), challenge.objective(),
                    String.join(", ", challenge.resources()), String.join(", ", challenge.constraints()),
                    String.join(", ", challenge.successCriteria()),
                    teammateList, player.getGameSummary(), player.getRelationsSummary(),
                    gameConfig.limits().maxChallengeIdeaWords()
            )));

            String rawResponse = stripThinking(llmClient.chat(player.getModelId(), messages));
            String idea = parseJsonField(rawResponse, "idea", player.getName());
            pitches.add(new ChallengeIdea(player, idea));
            System.out.println("[PITCH] " + player.getName() + ": \"" + idea + "\"");
        }
        return pitches;
    }

    // ---- Private Helpers ----

    private String formatTeammates(List<Player> players) {
        StringBuilder sb = new StringBuilder();
        for (Player p : players) {
            sb.append("- ").append(p.getName()).append(" (ID: ").append(p.getId()).append(")")
                    .append(" — ").append(p.getPersonalityType().getArchetype()).append("\n");
        }
        return sb.toString();
    }

    private String parseJsonField(String rawResponse, String fieldName, String playerName) {
        try {
            String cleaned = JsonUtils.extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has(fieldName)) {
                return node.get(fieldName).asText();
            }
            if (fieldName.equals("idea") && node.has("plan")) {
                return node.get("plan").asText();
            }
            if (fieldName.equals("plan") && node.has("idea")) {
                return node.get("idea").asText();
            }
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            if (fields.hasNext()) {
                return fields.next().getValue().asText();
            }
            return rawResponse;
        } catch (Exception e) {
            System.out.println("[WARN] Failed to parse JSON field '" + fieldName + "' for " + playerName + ", using raw response.");
            return rawResponse;
        }
    }

    private static String stripThinking(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
