package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.Team;
import com.akshaya.outlast.model.PlayerPick;
import com.akshaya.outlast.utils.ChatMessage;
import com.akshaya.outlast.utils.PromptMessage;
import com.akshaya.outlast.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.config.World;

@Component
public class SetUpTeams {

    private final LLMClient llmClient;
    private final GameContext gameContext;
    private final Environment environment;
    private final GameConfig gameConfig;
    private final World world;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    public SetUpTeams(LLMClient llmClient, GameContext gameContext, Environment environment, GameConfig gameConfig, World world) {
        this.llmClient = llmClient;
        this.gameContext = gameContext;
        this.environment = environment;
        this.gameConfig = gameConfig;
        this.world = world;
    }

    /**
     * Step 1: Creates teams and assigns captains.
     * Returns the list of teams with captains set but no other members yet.
     * Orchestrator calls host.announceCaptains() after this.
     */
    public List<Team> assignCaptains(int numberOfTeams) {
        List<Player> availablePlayers = new ArrayList<>(gameContext.getActivePlayers());
        if (availablePlayers.isEmpty()) {
            throw new IllegalStateException("No active players found in GameContext for team setup.");
        }
        Collections.shuffle(availablePlayers);

        Map<String, Team> teamsMap = createTeams(numberOfTeams);
        List<Team> teamsList = new ArrayList<>(teamsMap.values());

        setUpCaptains(availablePlayers, teamsList);
        return teamsList;
    }

    /**
     * Step 2: Runs the round-robin draft using LLM-driven captain picks.
     * Saves final teams to GameContext and returns them.
     * Orchestrator calls host.announceTeams() after this.
     */
    public List<Team> runDraft(List<Team> teamsList) {
        List<Player> allPlayers = new ArrayList<>(gameContext.getActivePlayers());
        List<Player> captains = teamsList.stream().map(Team::getCaptain).collect(Collectors.toList());
        List<Player> draftees = new ArrayList<>(allPlayers);
        captains.forEach(draftees::remove); // Draftees are all active players except captains

        System.out.println("\n--- Phase 1: Marketing Introductions ---");
        // 1. Display pre-generated marketing introductions from everyone (including captains)
        for (Player p : allPlayers) {
            System.out.println("[INTRO] " + p.getName() + " introduces: \"" + p.getIntroduction() + "\"");
        }

        System.out.println("\n--- Phase 2: Captain Preference Rankings ---");
        // 2. Query captains to rank draftees
        Map<String, List<String>> captainRankings = new HashMap<>(); // captainId -> list of draftee ids in order of preference
        for (Team team : teamsList) {
            Player captain = team.getCaptain();
            List<String> ranks = getCaptainPreferences(captain, draftees);
            captainRankings.put(captain.getId(), ranks);
            System.out.println("[RANKINGS] " + captain.getName() + " ranks candidates: " + ranks);
        }

        System.out.println("\n--- Phase 3: Programmatic Team Assignment ---");
        // 3. Programmatic round-robin draft
        List<Player> availablePool = new ArrayList<>(draftees);
        int roundIndex = 0;
        while (!availablePool.isEmpty()) {
            Team activeTeam = teamsList.get(roundIndex % teamsList.size());
            Player captain = activeTeam.getCaptain();
            List<String> prefs = captainRankings.get(captain.getId());
            
            // Find the highest-ranked player in prefs who is still in availablePool
            Player picked = null;
            if (prefs != null) {
                for (String preferredId : prefs) {
                    Player match = availablePool.stream()
                            .filter(p -> p.getId().equalsIgnoreCase(preferredId))
                            .findFirst()
                            .orElse(null);
                    if (match != null) {
                        picked = match;
                        break;
                    }
                }
            }
            
            // Fallback if none of their preferences are left or pref list was empty/invalid
            if (picked == null) {
                picked = availablePool.get(0);
            }

            activeTeam.addPlayer(picked);
            availablePool.remove(picked);
            System.out.println("--> Team " + activeTeam.getTeamName() + " (Captain: " + captain.getName() + ") drafts " + picked.getName() + "!");
            roundIndex++;
        }

        gameContext.setTeams(teamsList);

        System.out.println("\n======== FINAL TEAMS ========");
        for (Team team : teamsList) {
            System.out.println(team.getTeamName() + " (Captain: " + team.getCaptain().getName() + ")");
            for (Player p : team.getPlayers()) {
                System.out.println("- " + p.getName() + " (ID: " + p.getId() + ")");
            }
            System.out.println();
        }
        return teamsList;
    }

    private List<String> getCaptainPreferences(Player captain, List<Player> draftees) {
        StringBuilder pitches = new StringBuilder();
        StringBuilder validIdsBuilder = new StringBuilder();
        for (Player candidate : draftees) {
            pitches.append("- Contestant: ").append(candidate.getName())
                   .append(" (ID: ").append(candidate.getId()).append(")\n")
                   .append("  Traits: ").append(candidate.getPersonalityType().getTraits()).append("\n")
                   .append("  Introduction: \"").append(candidate.getIntroduction()).append("\"\n\n");
            validIdsBuilder.append(candidate.getId()).append(", ");
        }
        String validIds = validIdsBuilder.toString();
        if (validIds.endsWith(", ")) validIds = validIds.substring(0, validIds.length() - 2);

        String draftPhaseDesc = world.phases() != null ? world.phases().getOrDefault("draft", "Team Selection / Draft phase.") : "Team Selection / Draft phase.";

        List<PromptMessage> messages = new ArrayList<>();
        messages.add(new PromptMessage("system", captain.getSystemPrompt()));
        messages.add(new PromptMessage("user", """
                Current Phase: %s
                
                You are a team captain. You must rank the remaining contestants in order of who you want to draft onto your team (from #1 most desired to least desired).
                
                Contestants and their self-introductions:
                %s
                
                Evaluate their introductions, traits, and potential value to your team.
                Rank all contestants from first preference to last preference.
                
                You MUST return ONLY a valid JSON array of player IDs in order of preference (most desired first). Do not include any formatting, markdown, or other text outside the JSON.
                Example format:
                [ "id_1", "id_2", "id_3" ]
                
                Available IDs to rank: %s
                """.formatted(draftPhaseDesc, pitches.toString(), validIds)));

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String response = stripThinking(llmClient.chat(captain.getModelId(), messages));
                String cleaned = JsonUtils.extractJson(response);
                
                JsonNode arrayNode = objectMapper.readTree(cleaned);
                if (arrayNode.isArray()) {
                    List<String> ranks = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        String id = node.asText().trim();
                        if (id.toLowerCase().startsWith("contestant_")) {
                            id = id.substring("contestant_".length());
                        }
                        ranks.add(id);
                    }
                    if (!ranks.isEmpty()) {
                        return ranks;
                    }
                }
            } catch (Exception e) {
                System.out.println("[WARN] Attempt " + attempt + " failed to parse captain rankings: " + e.getMessage());
            }
        }

        // Fallback: shuffle draftees
        List<String> fallbackRanks = draftees.stream().map(Player::getId).collect(Collectors.toList());
        Collections.shuffle(fallbackRanks);
        return fallbackRanks;
    }

    /**
     * Step 2 (Testing bypass): Randomly assigns players to teams instead of drafting.
     */
    public List<Team> runRandomAssignment(List<Team> teamsList) {
        List<Player> availablePlayers = new ArrayList<>(gameContext.getActivePlayers());
        // Remove already-assigned captains
        teamsList.forEach(t -> availablePlayers.remove(t.getCaptain()));
        
        Collections.shuffle(availablePlayers);

        int teamIndex = 0;
        while (!availablePlayers.isEmpty()) {
            Team activeTeam = teamsList.get(teamIndex % teamsList.size());
            Player pick = availablePlayers.remove(0);
            activeTeam.addPlayer(pick);
            teamIndex++;
        }

        gameContext.setTeams(teamsList);

        System.out.println("\n======== FINAL TEAMS (RANDOM) ========");
        for (Team team : teamsList) {
            System.out.println(team.getTeamName() + " (Captain: " + team.getCaptain().getName() + ")");
            for (Player p : team.getPlayers()) {
                System.out.println("- " + p.getName() + " (ID: " + p.getId() + ")");
            }
            System.out.println();
        }
        return teamsList;
    }


    private void setUpCaptains(List<Player> availablePlayers, List<Team> teamsList) {
        Scanner scanner = new Scanner(System.in);
        boolean manualSetup = false;

        try {
            // Only prompt if not running in the automated test profile or web mode
            boolean isTestProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
            if (!isTestProfile && !gameContext.isWebMode()) {
                System.out.println("Do you want to manually set up captains? (yes/no): ");
                String choice = scanner.nextLine().trim();
                if (choice.equalsIgnoreCase("yes") || choice.equalsIgnoreCase("y")) {
                    manualSetup = true;

                    System.out.println("\nAvailable Players for Captain Selection:");
                    for (Player p : availablePlayers) {
                        System.out.println("ID: " + p.getId() + " | Name: " + p.getName() + " | Personality: " + p.getPersonalityType().getArchetype());
                    }
                    System.out.println();

                    for (Team team : teamsList) {
                        Player captain = null;
                        while (captain == null) {
                            System.out.println("Enter Player ID for " + team.getTeamName() + " Captain: ");
                            String id = scanner.nextLine().trim();
                            captain = findPlayerInList(availablePlayers, id);
                            if (captain == null) {
                                System.out.println("Invalid Player ID. Please select from the list.");
                            }
                        }
                        availablePlayers.remove(captain);
                        team.setCaptain(captain);
                        team.addPlayer(captain);
                        System.out.println("Assigned " + captain.getName() + " as Captain of " + team.getTeamName());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[INFO] Non-interactive environment, falling back to auto-selection of captains.");
        }

        if (!manualSetup) {
            System.out.println("Auto-selecting captains randomly:");
            Collections.shuffle(availablePlayers);
            for (Team team : teamsList) {
                Player captain = availablePlayers.remove(0);
                team.setCaptain(captain);
                team.addPlayer(captain);
                System.out.println(team.getTeamName() + " Captain: " + captain.getName());
            }
        }
    }


    private Player requestSelection(
            Player captain,
            List<Player> availablePlayers,
            String question,
            List<ChatMessage> answers,
            String validNames) {

        for (int attempt = 1; attempt <= 3; attempt++) {
            String selectionPhaseDesc = world.phases() != null ? world.phases().getOrDefault("draft", "Team Selection / Draft phase.") : "Team Selection / Draft phase.";
            StringBuilder selectionPrompt = new StringBuilder();

            selectionPrompt.append("""
Current Phase: %s

You asked the members: "%s".
You have now heard everyone's answers.

Choose EXACTLY ONE player to recruit from this list only. You MUST choose by their exact ID: %s

You MUST return ONLY the raw JSON object. Do not explain your choice outside the JSON. Do not write any thoughts, preambles, greetings, reasoning, or markdown formatting (do NOT use ```json or ```).

Required JSON schema (the "reason" value MUST be a brief, short (%d sentences maximum) in-character explanation of your selection reflecting your personality):
{"id":"chosen_player_id", "reason":"in-character explanation of selection"}

Answers:

""".formatted(selectionPhaseDesc, question, validNames, gameConfig.limits().maxReasonSentences()));

            for (ChatMessage answer : answers) {
                selectionPrompt.append(answer.from().getName())
                        .append(" (ID: ").append(answer.from().getId()).append("):\n")
                        .append(answer.message())
                        .append("\n\n");
            }

            if (attempt > 1) {
                selectionPrompt.append(
                        "\nIMPORTANT: Your previous response was invalid JSON or an invalid ID. "
                                + "You MUST return valid JSON with \"id\" set to one of the exact IDs listed above.\n");
            }

            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", captain.getSystemPrompt()));
            messages.add(new PromptMessage("user", selectionPrompt.toString()));

            String response = stripThinking(llmClient.chat(captain.getModelId(), messages));

            String cleaned = JsonUtils.extractJson(response);

            System.out.println();
            System.out.println("(attempt " + attempt + ") " + cleaned);
            System.out.println();

            try {
                PlayerPick pick = objectMapper.readValue(cleaned, PlayerPick.class);

                String pickedId = pick.id();
                if (pickedId != null) {
                    String finalPickedId = pickedId.trim();
                    Optional<Player> match = availablePlayers.stream()
                            .filter(p -> finalPickedId.equalsIgnoreCase(p.getId()))
                            .findFirst();

                    if (match.isPresent()) {
                        return match.get();
                    }
                }
            } catch (Exception e) {
                System.out.println("[WARN] Attempt " + attempt + " produced invalid JSON: " + e.getMessage());
            }
        }

        return null;
    }

    private static final List<String> DEFAULT_TEAM_NAMES = List.of(
        "Ruby", "Sapphire", "Emerald", "Onyx", "Topaz", "Opal", "Jade", "Garnet", "Quartz", "Pearl"
    );

    private Map<String, Team> createTeams(int numberOfTeams){
        Map<String, Team> teams = new HashMap<>();
        List<String> namesPool = new ArrayList<>(DEFAULT_TEAM_NAMES);
        Collections.shuffle(namesPool);
        for (int index = 0; index < numberOfTeams; index++) {
            Team team  = new Team(namesPool.get(index));
            teams.put(team.getTeamId(),team);
        }
        return teams;
    }

    private Player findPlayerInList(List<Player> list, String id) {
        return list.stream()
                .filter(p -> p.getId().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private static String stripThinking(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}