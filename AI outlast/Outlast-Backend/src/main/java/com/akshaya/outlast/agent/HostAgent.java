package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.config.World;
import com.akshaya.outlast.model.*;
import com.akshaya.outlast.prompt.HostSystemPromptBuilder;
import com.akshaya.outlast.utils.JsonUtils;
import com.akshaya.outlast.utils.PromptMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class HostAgent {

    private final LLMClient llmClient;
    private final HostSystemPromptBuilder hostSystemPromptBuilder;
    private final GameConfig gameConfig;
    private final World world;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    private final List<ChallengeTheme> themes;
    private final List<String> subCategories;
    private int roundIndex = 0;

    public HostAgent(LLMClient llmClient, HostSystemPromptBuilder hostSystemPromptBuilder, GameConfig gameConfig, World world) {
        this.llmClient = llmClient;
        this.hostSystemPromptBuilder = hostSystemPromptBuilder;
        this.gameConfig = gameConfig;
        this.world = world;

        this.themes = new ArrayList<>(Arrays.asList(ChallengeTheme.values()));
        Collections.shuffle(themes);

        this.subCategories = new ArrayList<>(List.of(
                "shelter-building", "fire-making", "foraging",
                "raft-building", "navigation", "trap-making"
        ));
        Collections.shuffle(subCategories);
    }

    public String welcomeContestants(List<Player> players) {
        // ---- [TEST MODE - commented out] ----
        // String welcome = "Welcome to the island. Outwit, outplay, outlast! Let the adventure begin.";
        // System.out.println("\n[HOST] " + welcome);
        // return welcome;
        // ---- END TEST MODE ----

        StringBuilder playerList = new StringBuilder();
        for (Player p : players) {
            playerList.append("- ").append(p.getName()).append(" (").append(p.getPersonalityType().getArchetype()).append(")\n");
        }
        String prompt = """
                Current Phase: Game Opening
                Setting: %s
                
                The following contestants have arrived on the island:
                %s
                
                Welcome them dramatically to the game. Build excitement and suspense.
                Keep it under %d words. Be punchy, charismatic, in-character as the Host.
                """.formatted(world.location(), playerList.toString(), gameConfig.limits().maxAnswerWords());

        String welcome;
        try {
            welcome = stripThinking(chat(prompt));
        } catch (Exception e) {
            welcome = "Welcome to the island. Outwit, outplay, outlast! Let the adventure begin.";
        }
        System.out.println("\n[HOST] " + welcome);
        return welcome;
    }

    /**
     * Host announces the captains and instructs them to begin the draft.
     */
    public String announceCaptains(List<Player> captains, List<Team> teams) {
        // ---- [TEST MODE - commented out] ----
        // String announcement = "Captains are set! Time to form your teams.";
        // System.out.println("\n[HOST] " + announcement);
        // return announcement;
        // ---- END TEST MODE ----

        StringBuilder captainList = new StringBuilder();
        for (int i = 0; i < captains.size(); i++) {
            captainList.append("- ").append(captains.get(i).getName())
                    .append(" is captain of ").append(teams.get(i).getTeamName()).append("\n");
        }
        String prompt = """
                Current Phase: Team Draft
                
                The captains have been chosen:
                %s
                
                Announce the captains dramatically and tell them to begin drafting their teams.
                Keep it under %d words. Be punchy and in-character as the Host.
                """.formatted(captainList.toString(), gameConfig.limits().maxAnswerWords());

        String announcement;
        try {
            announcement = stripThinking(chat(prompt));
        } catch (Exception e) {
            announcement = "Captains are set! Time to form your teams.";
        }
        System.out.println("\n[HOST] " + announcement);
        return announcement;
    }

    /**
     * Host announces the final team compositions after the draft.
     */
    public String announceTeams(List<Team> teams) {
        // ---- [TEST MODE - commented out] ----
        // String announcement = "The teams are locked! Go to your camps and prepare.";
        // System.out.println("\n[HOST] " + announcement);
        // return announcement;
        // ---- END TEST MODE ----

        StringBuilder teamList = new StringBuilder();
        for (Team team : teams) {
            teamList.append(team.getTeamName()).append(" (Captain: ").append(team.getCaptain().getName()).append("):\n");
            for (Player p : team.getPlayers()) {
                teamList.append("  - ").append(p.getName()).append("\n");
            }
        }
        String prompt = """
                Current Phase: Teams Locked
                
                The teams are now set:
                %s
                
                Dramatically announce the final teams and send them to their camps.
                Keep it under %d words. Be punchy and in-character as the Host.
                """.formatted(teamList.toString(), gameConfig.limits().maxAnswerWords());

        String announcement;
        try {
            announcement = stripThinking(chat(prompt));
        } catch (Exception e) {
            announcement = "The teams are locked! Go to your camps and prepare.";
        }
        System.out.println("\n[HOST] " + announcement);
        return announcement;
    }

    public Challenge createChallenge(boolean isMerge) {
        // ---- [TEST MODE - commented out] ----
        // Challenge challenge = new Challenge(
        //         ChallengeTheme.SURVIVAL, "Primitive Fire Making",
        //         "Build a fire using primitive techniques to boil water.",
        //         "Boil 1 liter of stream water.",
        //         List.of("Flint and Steel", "Dry tinder", "Firewood"),
        //         List.of("No modern tools", "Time limit: 60 minutes", "Must keep the fire lit for 5 minutes"),
        //         List.of("First team to boil water wins immunity")
        // );
        // System.out.println("\n[HOST] Created Challenge: " + challenge.title());
        // return challenge;
        // ---- END TEST MODE ----

        ChallengeTheme theme = themes.get(roundIndex % themes.size());
        String subCategory = subCategories.get(roundIndex % subCategories.size());
        roundIndex++;

        String prompt = """
                Current Phase: Challenge Creation
                Setting: %s
                Theme: %s
                Sub-category hint: %s
                
                Create a unique, dramatic survival challenge for the contestants.
                Respond with ONLY valid JSON:
                {
                  "title": "Short punchy challenge name",
                  "scenario": "1-2 sentence dramatic scenario setup",
                  "objective": "The clear win condition in one sentence",
                  "materials": ["item1", "item2", "item3"],
                  "rules": ["rule1", "rule2"],
                  "rewards": ["reward1"]
                }
                """.formatted(world.location(), theme.name(), subCategory);

        try {
            String raw = stripThinking(chat(prompt));
            String cleaned = JsonUtils.extractJson(raw);
            JsonNode node = objectMapper.readTree(cleaned);

            String title = node.get("title").asText();
            String scenario = node.get("scenario").asText();
            String objective = node.get("objective").asText();
            List<String> materials = new ArrayList<>();
            List<String> rules = new ArrayList<>();
            List<String> rewards = new ArrayList<>();
            node.get("materials").forEach(n -> materials.add(n.asText()));
            node.get("rules").forEach(n -> rules.add(n.asText()));
            node.get("rewards").forEach(n -> rewards.add(n.asText()));

            Challenge challenge = new Challenge(theme, title, scenario, objective, materials, rules, rewards);
            System.out.println("\n[HOST] Challenge Created: " + challenge.title());
            System.out.println("[HOST] Scenario: " + challenge.scenario());
            System.out.println("[HOST] Objective: " + challenge.objective());
            return challenge;
        } catch (Exception e) {
            System.out.println("[WARN] Host failed to create challenge via LLM, using config fallback.");
            
            GameConfig.FallbackChallenge fallbackCfg;
            if (isMerge) {
                List<GameConfig.FallbackChallenge> fallbacks = gameConfig.mergeFallbacks();
                fallbackCfg = (fallbacks != null && !fallbacks.isEmpty())
                        ? fallbacks.get(roundIndex % fallbacks.size())
                        : new GameConfig.FallbackChallenge("Survival Test", "SURVIVAL", "Push your limits in the wild.", "Complete the task.", List.of("Water"), List.of("60 minutes"), List.of("Immunity"));
            } else {
                List<GameConfig.FallbackChallenge> fallbacks = gameConfig.teamFallbacks();
                fallbackCfg = (fallbacks != null && !fallbacks.isEmpty())
                        ? fallbacks.get(roundIndex % fallbacks.size())
                        : new GameConfig.FallbackChallenge("Survival Test", "SURVIVAL", "Push your limits in the wild.", "Complete the task.", List.of("Water"), List.of("60 minutes"), List.of("Immunity"));
            }

            ChallengeTheme fallbackTheme;
            try {
                fallbackTheme = ChallengeTheme.valueOf(fallbackCfg.theme().toUpperCase());
            } catch (Exception ex) {
                fallbackTheme = theme;
            }

            Challenge fallback = new Challenge(
                    fallbackTheme,
                    fallbackCfg.title(),
                    fallbackCfg.scenario(),
                    fallbackCfg.objective(),
                    fallbackCfg.materials(),
                    fallbackCfg.rules(),
                    fallbackCfg.rewards()
            );
            System.out.println("\n[HOST] Challenge Created (Fallback): " + fallback.title());
            System.out.println("[HOST] Scenario (Fallback): " + fallback.scenario());
            return fallback;
        }
    }

    /**
     * Host judges the challenge based on final team plans.
     */
    public ChallengeResult judgeChallenge(List<Team> teams, Map<String, UpgradedIdea> teamPlans, Challenge challenge) {
        List<Team> competingTeams = teams.stream().filter(t -> !t.getPlayers().isEmpty()).toList();
        if (competingTeams.size() < 2) throw new IllegalArgumentException("A team challenge needs at least two active tribes.");
        StringBuilder plansSummary = new StringBuilder();
        for (Team team : competingTeams) {
            UpgradedIdea plan = teamPlans.get(team.getTeamName());
            plansSummary.append("Team: ").append(team.getTeamName())
                    .append(" (Plan by ").append(plan.player().getName()).append("):\n")
                    .append("\"").append(plan.plan()).append("\"\n\n");
        }

        String userPrompt = """
                Current Phase: Challenge Judging
                
                Challenge details:
                - Title: %s
                - Scenario: %s
                - Objective: %s
                
                Teammate Plans submitted:
                %s
                
                Evaluate all submitted plans. Decide which plan is logically superior, safer, and most likely to succeed.
                Select exactly ONE winning team and exactly ONE losing team.
                
                Respond with ONLY valid JSON:
                {
                  "winningTeamName": "winning_team_name_exactly",
                  "losingTeamName": "losing_team_name_exactly",
                  "reasoning": "dramatic 2-3 sentence explanation of the win"
                }
                """.formatted(challenge.title(), challenge.scenario(), challenge.objective(), plansSummary.toString());

        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", hostSystemPromptBuilder.build()));
        messages.add(new PromptMessage("user", userPrompt));

        try {
            String rawResponse = stripThinking(chat(userPrompt));
            String cleaned = JsonUtils.extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(cleaned);
            String winner = node.get("winningTeamName").asText();
            String loser = node.get("losingTeamName").asText();
            String reasoning = node.get("reasoning").asText();

            Team winnerTeam = competingTeams.stream().filter(t -> t.getTeamName().equalsIgnoreCase(winner.trim())).findFirst().orElseThrow();
            Team loserTeam = competingTeams.stream().filter(t -> t.getTeamName().equalsIgnoreCase(loser.trim())).findFirst().orElseThrow();
            if (winnerTeam == loserTeam) throw new IllegalArgumentException("Winner and loser must be different tribes");
            String finalWinner = winnerTeam.getTeamName();
            String finalLoser = loserTeam.getTeamName();

            ChallengeResult result = new ChallengeResult(finalWinner, finalLoser, reasoning);
            System.out.println("\n[HOST JUDGING] Winner: " + result.winningTeamName() + " | Loser: " + result.losingTeamName());
            System.out.println("[HOST JUDGING] Reasoning: " + result.reasoning());
            return result;
        } catch (Exception e) {
            System.out.println("[WARN] Host failed to parse judging response, using cooperation stat fallback.");
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Simulation cancelled");
            List<Team> ordered = new ArrayList<>(competingTeams);
            Collections.shuffle(ordered);
            ordered.sort(Comparator.comparingDouble(this::averageCooperation));
            Team loserTeam = ordered.get(0);
            Team winnerTeam = ordered.get(ordered.size() - 1);
            
            ChallengeResult result = new ChallengeResult(
                winnerTeam.getTeamName(), 
                loserTeam.getTeamName(), 
                winnerTeam.getTeamName() + " demonstrated superior team cooperation and synchronization in their strategy execution (avg cooperation: " + (int)Math.round(averageCooperation(winnerTeam)) + ")."
            );
            System.out.println("\n[HOST JUDGING (FALLBACK)] Winner: " + result.winningTeamName() + " | Loser: " + result.losingTeamName());
            System.out.println("[HOST JUDGING (FALLBACK)] Reasoning: " + result.reasoning());
            return result;
        }
    }

    /**
     * Host ranks the individual player strategy pitches for immunity challenges.
     */
    public MergeChallengeResult rankIndividualChallenge(List<Player> players, Map<String, String> pitches, Challenge challenge) {
        StringBuilder pitchesSummary = new StringBuilder();
        Map<String, Player> playerMap = new HashMap<>();
        for (Player p : players) {
            playerMap.put(p.getId(), p);
            String plan = pitches.get(p.getId());
            pitchesSummary.append("- Player: ").append(p.getName()).append(" (ID: ").append(p.getId()).append("):\n")
                    .append("  \"").append(plan).append("\"\n\n");
        }

        String userPrompt = """
                Current Phase: Individual Challenge Judging
                
                Challenge Details:
                - Title: %s
                - Scenario: %s
                - Objective: %s
                
                Contestant Pitches:
                %s
                
                Evaluate and rank the contestant pitches from 1 (best strategy/likely to succeed) to %d (worst/most flawed strategy).
                Do not allow ties. Assign unique ranks (1, 2, ..., %d).
                
                Respond with ONLY valid JSON array:
                [
                  {
                    "playerId": "1",
                    "rank": 1,
                    "reasoning": "Brief explanation of this rank."
                  }
                ]
                Use the exact player ID (e.g. "1", "2") in the "playerId" field.
                """.formatted(challenge.title(), challenge.scenario(), challenge.objective(), pitchesSummary.toString(), players.size(), players.size());

        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", hostSystemPromptBuilder.build()));
        messages.add(new PromptMessage("user", userPrompt));

        String rawResponse = "";
        List<PlayerRanking> rankings = new ArrayList<>();

        try {
            rawResponse = stripThinking(chat(userPrompt));
            JsonNode arrayNode = objectMapper.readTree(JsonUtils.extractJson(rawResponse));
            if (arrayNode.isArray()) {
                for (JsonNode node : arrayNode) {
                    String pId = null;
                    if (node.has("playerId")) {
                        pId = node.get("playerId").asText().trim();
                    } else if (node.has("id")) {
                        pId = node.get("id").asText().trim();
                    } else if (node.has("player")) {
                        pId = node.get("player").asText().trim();
                    }

                    if (pId == null) {
                        // Fallback: try to find any field value matching player names or IDs
                        java.util.Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String val = field.getValue().asText().trim();
                            for (Player p : players) {
                                if (p.getId().equals(val) || p.getName().equalsIgnoreCase(val)) {
                                    pId = val;
                                    break;
                                }
                            }
                            if (pId != null) break;
                        }
                    }

                    if (pId == null) continue;

                    if (pId.toLowerCase().startsWith("contestant_")) {
                        pId = pId.substring("contestant_".length());
                    }
                    
                    Player player = playerMap.get(pId);
                    if (player == null) {
                        String numericId = pId.replaceAll("\\D+", "");
                        player = playerMap.get(numericId);
                    }
                    if (player == null) {
                        for (Player p : players) {
                            if (p.getName().equalsIgnoreCase(pId) || pId.toLowerCase().contains(p.getName().toLowerCase())) {
                                player = p;
                                break;
                            }
                        }
                    }

                    int rank = rankings.size() + 1;
                    if (node.has("rank")) {
                        rank = node.get("rank").asInt();
                    }

                    String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "Stable performance.";
                    
                    if (player != null) {
                        final Player targetPlayer = player;
                        boolean alreadyRanked = rankings.stream().anyMatch(r -> r.player().getId().equals(targetPlayer.getId()));
                        if (!alreadyRanked) {
                            rankings.add(new PlayerRanking(player, rank, reasoning));
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Simulation cancelled");
            System.out.println("[WARN] Host failed to parse rankings: " + e.getMessage() + ".");
        }

        // Check if rankings is valid, otherwise fill and sort using Survival Grit stat
        if (rankings.size() != players.size()
                || rankings.stream().map(PlayerRanking::rank).distinct().count() != players.size()
                || rankings.stream().anyMatch(r -> r.rank() < 1 || r.rank() > players.size())) {
            System.out.println("[WARN] Rankings are incomplete or contain invalid positions. Fallback to Survival Grit sorting.");
            System.out.println("[DEBUG] Raw response was:\n" + rawResponse);
            rankings.clear();
            List<Player> sortedByGrit = new ArrayList<>(players);
            sortedByGrit.sort((a, b) -> Integer.compare(
                b.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50),
                a.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50)
            ));
            for (int i = 0; i < sortedByGrit.size(); i++) {
                Player p = sortedByGrit.get(i);
                int grit = p.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50);
                rankings.add(new PlayerRanking(p, i + 1, "Executed strategy with physical toughness and high grit (Survival Grit: " + grit + ")."));
            }
        } else {
            // Sort by rank ascending
            rankings.sort(Comparator.comparingInt(PlayerRanking::rank));
        }

        Player immunityWinner = rankings.get(0).player();
        immunityWinner.incrementIndividualWins();
        Player challengeLoser = rankings.get(rankings.size() - 1).player();

        System.out.println("\n--- CHALLENGE RANKINGS ---");
        for (PlayerRanking pr : rankings) {
            System.out.println("Rank #" + pr.rank() + ": " + pr.player().getName() + " — Reason: \"" + pr.reasoning() + "\"");
        }
        System.out.println("[HOST ANNOUNCEMENT] " + immunityWinner.getName() + " has won INDIVIDUAL IMMUNITY! They are safe from voting tonight.");
        System.out.println("[HOST ANNOUNCEMENT] " + challengeLoser.getName() + " came in last place.");

        return new MergeChallengeResult(rankings, immunityWinner, challengeLoser);
    }

    private double averageCooperation(Team team) {
        return team.getPlayers().stream().mapToInt(p -> p.getStat(StatType.COOPERATION)).average().orElse(0);
    }

    // --- Shared helpers ---

    private String chat(String userPrompt) {
        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", hostSystemPromptBuilder.build()));
        messages.add(new PromptMessage("user", userPrompt));
        return llmClient.chat(gameConfig.host().modelId(), messages);
    }

    private String stripThinking(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}