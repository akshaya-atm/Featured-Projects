package com.akshaya.outlast;

import com.akshaya.outlast.agent.SetUpTeams;
import com.akshaya.outlast.agent.HostAgent;
import com.akshaya.outlast.agent.CampLifePhase;
import com.akshaya.outlast.agent.GameInitializer;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.Team;
import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.PlayerInitRequest;
import com.akshaya.outlast.utils.Gender;
import com.akshaya.outlast.utils.IntroMessage;
import com.akshaya.outlast.model.SecretMessage;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import com.akshaya.outlast.agent.ChallengePhase;
import com.akshaya.outlast.agent.TribalCouncilPhase;
import com.akshaya.outlast.model.Challenge;
import com.akshaya.outlast.model.ChallengeResult;
import com.akshaya.outlast.model.UpgradedIdea;
import com.akshaya.outlast.model.Vote;
import com.akshaya.outlast.model.TeamChallengeResult;
import com.akshaya.outlast.model.ChallengeIdea;
import com.akshaya.outlast.model.MergeChallengeResult;
import com.akshaya.outlast.model.PlayerRanking;
import com.akshaya.outlast.model.StatType;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import org.springframework.core.env.Environment;

@Component
public class GameSimulationRunner implements CommandLineRunner {

    private final GameInitializer gameInitializer;
    private final SetUpTeams setUpTeams;
    private final HostAgent hostAgent;
    private final CampLifePhase campLifePhase;
    private final ChallengePhase challengePhase;
    private final TribalCouncilPhase tribalCouncilPhase;
    private final GameContext gameContext;
    private final Environment environment;

    public GameSimulationRunner(GameInitializer gameInitializer, SetUpTeams setUpTeams,
                             HostAgent hostAgent, CampLifePhase campLifePhase,
                             ChallengePhase challengePhase, TribalCouncilPhase tribalCouncilPhase,
                             GameContext gameContext, Environment environment) {
        this.gameInitializer = gameInitializer;
        this.setUpTeams = setUpTeams;
        this.hostAgent = hostAgent;
        this.campLifePhase = campLifePhase;
        this.challengePhase = challengePhase;
        this.tribalCouncilPhase = tribalCouncilPhase;
        this.gameContext = gameContext;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        boolean autoStart = Boolean.parseBoolean(environment.getProperty("game.auto-start", "true"));
        if (!autoStart) {
            System.out.println("[INFO] Web mode active. Bypassing automatic console game start.");
            return;
        }

        int numberOfTeams = 2;
        int numberOfPlayers = 6;

        java.util.Scanner scanner = new java.util.Scanner(System.in);

        // ---- REAL-TIME INTERACTIVE SETUP ----
        boolean useDefaults = true;
        try {
            boolean isTestProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("test");
            if (isTestProfile) {
                System.out.println("[INFO] Test profile detected. Bypassing prompt and using defaults (2 teams, 6 players).");
            } else {
                System.out.println("Use default setup? (2 teams, 6 players, auto names/genders) (y/n, default: y): ");
                String defaultChoice = scanner.nextLine().trim().toLowerCase();

                if (defaultChoice.equals("no") || defaultChoice.equals("n")) {
                    useDefaults = false;
                    // Ask for team and player counts
                    boolean valid = false;
                    while (!valid) {
                        try {
                            System.out.println("Enter number of teams (min 2, max 4): ");
                            numberOfTeams = Integer.parseInt(scanner.nextLine().trim());
                            if (numberOfTeams < 2 || numberOfTeams > 4) throw new IllegalArgumentException("Teams must be 2-4.");

                            int minPlayers = numberOfTeams * 2;
                            System.out.println("Enter number of players (min " + minPlayers + ", max 16, multiple of " + numberOfTeams + "): ");
                            numberOfPlayers = Integer.parseInt(scanner.nextLine().trim());
                            if (numberOfPlayers < minPlayers || numberOfPlayers > 16) throw new IllegalArgumentException("Players must be between " + minPlayers + " and 16.");
                            if (numberOfPlayers % numberOfTeams != 0) throw new IllegalArgumentException("Players must be a multiple of " + numberOfTeams + ".");
                            valid = true;
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid input. Enter valid integers.");
                        } catch (IllegalArgumentException e) {
                            System.out.println("Invalid: " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[INFO] Falling back to defaults (2 teams, 6 players): " + e.getMessage());
        }

        List<PlayerInitRequest> requests = new ArrayList<>();
        try {
            if (useDefaults) {
                // Auto-generate contestants
                System.out.println("[INFO] Using default setup: " + numberOfTeams + " teams, " + numberOfPlayers + " players (auto-named).");
                for (int i = 1; i <= numberOfPlayers; i++) {
                    Gender gender = (i % 2 == 0) ? Gender.FEMALE : Gender.MALE;
                    requests.add(new PlayerInitRequest("Contestant_" + i, gender));
                }
            } else {
                // Ask user for each contestant's name and gender
                System.out.println("\n--- ENTER CONTESTANT DETAILS ---");
                for (int i = 1; i <= numberOfPlayers; i++) {
                    System.out.println("Enter name for Contestant " + i + " (or press Enter for auto): ");
                    String name = scanner.nextLine().trim();
                    if (name.isEmpty()) {
                        name = "Contestant_" + i;
                    }

                    Gender gender = null;
                    while (gender == null) {
                        System.out.println("Enter gender for " + name + " (M/F): ");
                        String gStr = scanner.nextLine().trim().toUpperCase();
                        if (gStr.equals("M")) {
                            gender = Gender.MALE;
                        } else if (gStr.equals("F")) {
                            gender = Gender.FEMALE;
                        } else {
                            System.out.println("Invalid. Enter M or F.");
                        }
                    }
                    requests.add(new PlayerInitRequest(name, gender));
                }
            }
        } catch (Exception e) {
            System.out.println("[INFO] Falling back to auto-generated contestants: " + e.getMessage());
            requests.clear();
            for (int i = 1; i <= numberOfPlayers; i++) {
                Gender gender = (i % 2 == 0) ? Gender.FEMALE : Gender.MALE;
                requests.add(new PlayerInitRequest("Contestant_" + i, gender));
            }
        }

        com.akshaya.outlast.model.GameConfigOptions options = new com.akshaya.outlast.model.GameConfigOptions();
        options.setUseDefaults(useDefaults);
        options.setNumberOfTeams(numberOfTeams);
        options.setNumberOfPlayers(numberOfPlayers);
        List<com.akshaya.outlast.model.GameConfigOptions.PlayerDetails> details = new ArrayList<>();
        for (PlayerInitRequest r : requests) {
            com.akshaya.outlast.model.GameConfigOptions.PlayerDetails d = new com.akshaya.outlast.model.GameConfigOptions.PlayerDetails();
            d.setName(r.name());
            d.setGender(r.gender());
            details.add(d);
        }
        options.setCustomPlayers(details);
        options.setStepByStep(false);

        runGame(options);
    }

    public void runGame(com.akshaya.outlast.model.GameConfigOptions options) {
        options.validate();
        tribalCouncilPhase.reset();
        int numberOfTeams = options.getNumberOfTeams();
        int numberOfPlayers = options.getNumberOfPlayers();
        boolean useDefaults = options.isUseDefaults();

        List<PlayerInitRequest> requests = new ArrayList<>();
        if (useDefaults || options.getCustomPlayers() == null || options.getCustomPlayers().isEmpty()) {
            for (int i = 1; i <= numberOfPlayers; i++) {
                Gender gender = (i % 2 == 0) ? Gender.FEMALE : Gender.MALE;
                requests.add(new PlayerInitRequest("Contestant_" + i, gender));
            }
        } else {
            for (com.akshaya.outlast.model.GameConfigOptions.PlayerDetails d : options.getCustomPlayers()) {
                requests.add(new PlayerInitRequest(d.getName(), d.getGender()));
            }
        }

        gameContext.setStatus("RUNNING");
        gameContext.setCurrentPhase("Initialization");

        try {
            System.out.println("\n--- INITIALIZING PLAYERS ---");
            gameInitializer.initializePlayers(requests);
            System.out.println("----------------------------");

            // Host welcomes contestants and announces the rules
            hostAgent.welcomeContestants(gameContext.getActivePlayers());

            System.out.println("\n--- RUNNING TEAM DRAFT ---");

            // Step 1: Assign captains
            List<Team> teams = setUpTeams.assignCaptains(numberOfTeams);

            // Host announces captains and instructs draft to begin
            List<Player> captains = teams.stream()
                    .map(Team::getCaptain)
                    .toList();
            hostAgent.announceCaptains(captains, teams);

            // Step 2: Run the actual LLM draft
            teams = setUpTeams.runDraft(teams);

            // Host announces the final teams
            hostAgent.announceTeams(teams);

            System.out.println("--------------------------");

            gameContext.checkpoint("Draft Phase Complete");

            // ========== CAMP LIFE ROUND 1 ==========
            System.out.println("\n--- CAMP LIFE ROUND 1: INTRODUCTIONS & SECRET MESSAGES ---");
            Map<String, List<IntroMessage>> teamIntros = new HashMap<>();
            for (Team team : teams) {
                System.out.println("\n========================================");
                System.out.println(">> " + team.getTeamName() + " Camp Life Phase <<");
                System.out.println("========================================");

                // Initialize relations first (trust = 50)
                for (Player player : team.getPlayers()) {
                    player.initializeRelations(team.getPlayers());
                }

                // Format intros context from pre-generated draft introductions
                StringBuilder introsContextBuilder = new StringBuilder();
                introsContextBuilder.append("Teammate Introductions:\n");
                for (Player p : team.getPlayers()) {
                    introsContextBuilder.append("- ").append(p.getName()).append(": \"")
                            .append(p.getIntroduction()).append("\"\n");
                }
                String introsContext = introsContextBuilder.toString();

                // Run secret messages (handles messaging + memory updates in a single LLM call)
                System.out.println("\n--- Running Secret Messages & Memory Upgrades ---");
                campLifePhase.runSecretMessages(team.getPlayers(), introsContext);
            }
            System.out.println("--------------------------------");

            gameContext.checkpoint("Camp Life Introductions Complete");

            // ========== GAME ROUNDS LOOP ==========
            int initialPlayerCount = gameContext.getActivePlayers().size();
            int mergeThreshold = (int) Math.ceil(initialPlayerCount * 0.70);

            System.out.println("\n========================================");
            System.out.println("Starting Game Rounds Loop");
            System.out.println("Initial Players: " + initialPlayerCount + " | Merge Threshold (70%): " + mergeThreshold);

            int round = 1;
            List<Player> lastEliminated = new ArrayList<>();
            while (gameContext.getActivePlayers().size() > mergeThreshold
                    && teams.stream().filter(t -> !t.getPlayers().isEmpty()).count() > 1) {
                gameContext.setCurrentRound(round);
                System.out.println("\n========================================");
                System.out.println(">>> ROUND " + round + " <<<");
                System.out.println("========================================");

                // Add global news from the previous round
                StringBuilder globalNews = new StringBuilder();
                if (!lastEliminated.isEmpty()) {
                    globalNews.append("Global News: The following player(s) were eliminated in the previous round: ");
                    for (Player p : lastEliminated) {
                        globalNews.append(p.getName()).append(" ");
                    }
                    globalNews.append("\n\n");
                } else if (round > 1) {
                    globalNews.append("Global News: No one was eliminated in the previous round.\n\n");
                }
                if (round > 1 && tribalCouncilPhase.getLastTribalTally() != null && !tribalCouncilPhase.getLastTribalTally().isEmpty()) {
                    globalNews.append(tribalCouncilPhase.getLastTribalTally()).append("\n");
                }
                String globalNewsStr = globalNews.toString();

                // 1. Propose challenge
                Challenge challenge = hostAgent.createChallenge(false);

                // 2. Pitch, Upgrade, and Vote challenge ideas per team
                Map<String, UpgradedIdea> teamPlans = new HashMap<>();
                Map<String, TeamChallengeResult> teamBrainstorms = new HashMap<>();
                for (Team team : teams) {
                    if (team.getPlayers().isEmpty()) continue;
                    TeamChallengeResult finalRes = challengePhase.runTeamChallengeFlow(team, challenge);
                    teamPlans.put(team.getTeamName(), finalRes.finalPlan());
                    teamBrainstorms.put(team.getTeamName(), finalRes);
                }

                // 3. Host Judging
                ChallengeResult result = hostAgent.judgeChallenge(teams, teamPlans, challenge);

                gameContext.checkpoint("Challenge Judging Complete");

                // 4. Camp Life: Secret Messages (bonding & alliance building)
                System.out.println("\n--- Camp Life Round: Secret Messages ---");

                for (Team team : teams) {
                    if (team.getPlayers().isEmpty()) continue;

                    // Only the WINNING team runs Camp Life Secret Messages!
                    // The losing team goes straight to strategy discussions.
                    if (team.getTeamName().equalsIgnoreCase(result.losingTeamName())) {
                        System.out.println("\n--> Team " + team.getTeamName() + " lost the challenge; skipping Camp Life messages to prepare for Tribal Council.");
                        continue;
                    }

                    TeamChallengeResult teamRes = teamBrainstorms.get(team.getTeamName());
                    StringBuilder detailsBuilder = new StringBuilder();
                    detailsBuilder.append(globalNewsStr);
                    detailsBuilder.append("Challenge Results:\n")
                            .append("- Winner: ").append(result.winningTeamName()).append("\n")
                            .append("- Loser: ").append(result.losingTeamName()).append("\n")
                            .append("- Host Judging Reasoning: ").append(result.reasoning()).append("\n\n");

                    detailsBuilder.append("Teammate Brainstorming Events from your team:\n");
                    detailsBuilder.append("Initial Pitches:\n");
                    for (ChallengeIdea pitch : teamRes.pitches()) {
                        detailsBuilder.append("- ").append(pitch.player().getName()).append(": \"").append(pitch.idea()).append("\"\n");
                    }
                    detailsBuilder.append("\nUpgraded Plans:\n");
                    for (UpgradedIdea upgrade : teamRes.upgrades()) {
                        detailsBuilder.append("- ").append(upgrade.player().getName()).append(": \"").append(upgrade.plan()).append("\"\n");
                    }
                    detailsBuilder.append("\nStrategy Votes Cast:\n");
                    for (Map.Entry<String, String> entry : teamRes.votes().entrySet()) {
                        Player voter = team.getPlayers().stream().filter(p -> p.getId().equals(entry.getKey())).findFirst().orElse(null);
                        Player votedForPlanOwner = team.getPlayers().stream().filter(p -> p.getId().equals(entry.getValue())).findFirst().orElse(null);
                        detailsBuilder.append("- ").append(voter != null ? voter.getName() : entry.getKey())
                                .append(" voted for ").append(votedForPlanOwner != null ? votedForPlanOwner.getName() : entry.getValue()).append("'s upgraded plan.\n");
                    }

                    String campLifeContext = detailsBuilder.toString();
                    System.out.println("\n--- Running Camp Life Secret Messages & Memory Upgrades for Team " + team.getTeamName() + " ---");
                    campLifePhase.runSecretMessages(team.getPlayers(), campLifeContext);
                }

                gameContext.checkpoint("Camp Life Complete");

                // 5. Determine Tribal Council Twist
                String twist = determineTwist(round);

                // Find losing team
                Team losingTeam = teams.stream()
                        .filter(t -> t.getTeamName().equalsIgnoreCase(result.losingTeamName()))
                        .findFirst()
                        .orElse(null);

                List<Vote> votes = new ArrayList<>();
                List<Player> eliminated = new ArrayList<>();

                if (losingTeam != null && !losingTeam.getPlayers().isEmpty()) {
                    if (twist.equalsIgnoreCase("LUCKY_NO_ELIMINATION")) {
                        System.out.println("\n[HOST TWIST] Tonight is a LUCKY_NO_ELIMINATION round! Team " + losingTeam.getTeamName() + " is safe. There will be no Tribal Council vote tonight!");
                    } else {
                        if (twist.equalsIgnoreCase("DOUBLE_ELIMINATION")) {
                            System.out.println("\n[HOST TWIST] Tonight is a DOUBLE_ELIMINATION round! TWO contestants will be voted out tonight!");
                        }

                        // Prepare context for losing team strategy conversations (which covers challenge result + brainstorming)
                        TeamChallengeResult losingTeamBrainstorm = teamBrainstorms.get(losingTeam.getTeamName());
                        StringBuilder losingContextBuilder = new StringBuilder();
                        losingContextBuilder.append(globalNewsStr);
                        losingContextBuilder.append("Challenge Results:\n")
                                .append("- Winner: ").append(result.winningTeamName()).append("\n")
                                .append("- Loser: ").append(result.losingTeamName()).append("\n")
                                .append("- Host Judging Reasoning: ").append(result.reasoning()).append("\n\n");

                        losingContextBuilder.append("Teammate Brainstorming Events from your team:\n");
                        for (ChallengeIdea pitch : losingTeamBrainstorm.pitches()) {
                            losingContextBuilder.append("- ").append(pitch.player().getName()).append(": \"").append(pitch.idea()).append("\"\n");
                        }

                        // 6. Tribal Council voting strategy messages
                        tribalCouncilPhase.runStrategyConversations(losingTeam, null, losingContextBuilder.toString());

                        // 7. Cast votes
                        votes = tribalCouncilPhase.castVotes(losingTeam);

                        // 8. Process eliminations
                        eliminated = tribalCouncilPhase.processVotes(losingTeam, votes, twist);
                    }
                }

                // Record who got eliminated for the next round's global news
                lastEliminated = eliminated;

                gameContext.checkpoint("Tribal Council Complete");

                round++;
            }

            System.out.println("\n========================================");
            System.out.println("Merge threshold reached! Moving to Merge Phase!");
            System.out.println("Active players remaining: " + gameContext.getActivePlayers().size());
            System.out.println("========================================");

            // ========== TRANSITION TO MERGE PHASE ==========
            System.out.println("\n*** MERGING TEAMS INTO ONE TRIBE ***");
            Team mergedTeam = new Team("Merged Tribe");
            for (Player p : gameContext.getActivePlayers()) {
                mergedTeam.addPlayer(p);
            }
            gameContext.setTeams(List.of(mergedTeam));

            // Initialize merged relations so everyone has trust scores for new tribe members
            for (Player p : mergedTeam.getPlayers()) {
                p.initializeRelations(mergedTeam.getPlayers());
            }

            gameContext.checkpoint("Merge Phase Initialized");

            // ========== MERGE ROUND LOOP ==========
            int mergeRound = 1;
            while (gameContext.getActivePlayers().size() > 2) {
                gameContext.setCurrentRound(round);
                System.out.println("\n========================================");
                System.out.println(">>> MERGE ROUND " + mergeRound + " <<<");
                System.out.println("========================================");

                // 1. Propose challenge
                Challenge challenge = hostAgent.createChallenge(true);

                // 2. Individual strategy pitches
                Map<String, String> pitches = challengePhase.runIndividualChallenge(mergedTeam.getPlayers(), challenge);

                // 3. Host Judging & Ranking
                MergeChallengeResult mergeResult = hostAgent.rankIndividualChallenge(mergedTeam.getPlayers(), pitches, challenge);

                gameContext.checkpoint("Immunity Challenge Ranked");

                // 4. Build challenge context for strategy discussions
                StringBuilder mergeCampBuilder = new StringBuilder();

                // Add global news of previous round
                if (!lastEliminated.isEmpty()) {
                    mergeCampBuilder.append("Global News: The following player(s) were eliminated in the previous round: ");
                    for (Player p : lastEliminated) {
                        mergeCampBuilder.append(p.getName()).append(" ");
                    }
                    mergeCampBuilder.append("\n\n");
                } else if (mergeRound > 1) {
                    mergeCampBuilder.append("Global News: No one was eliminated in the previous round.\n\n");
                }
                if (tribalCouncilPhase.getLastTribalTally() != null && !tribalCouncilPhase.getLastTribalTally().isEmpty()) {
                    mergeCampBuilder.append(tribalCouncilPhase.getLastTribalTally()).append("\n");
                }

                // If first round of merge, welcome them and give them the introductions of their new tribemates
                if (mergeRound == 1) {
                    mergeCampBuilder.append("Welcome to the Merged Tribe! All players from both teams have merged. ")
                            .append("Here are the self-marketing introductions of all contestants in the Merged Tribe:\n");
                    for (Player p : mergedTeam.getPlayers()) {
                        mergeCampBuilder.append("- ").append(p.getName()).append(": \"")
                                .append(p.getIntroduction()).append("\"\n");
                    }
                    mergeCampBuilder.append("\n");
                }

                mergeCampBuilder.append("Challenge Results:\n")
                        .append("- Immunity Winner: ").append(mergeResult.immunityWinner().getName()).append(" (is SAFE from voting tonight)\n")
                        .append("- Last Place: ").append(mergeResult.challengeLoser().getName()).append("\n\n")
                        .append("Official Rankings from Host:\n");
                for (PlayerRanking pr : mergeResult.rankings()) {
                    mergeCampBuilder.append("- Rank #").append(pr.rank()).append(": ").append(pr.player().getName())
                            .append(" (Reason: \"").append(pr.reasoning()).append("\")\n");
                }
                String mergeCampContext = mergeCampBuilder.toString();

                // 5. Determine Twist
                String twist = determineMergeTwist(mergeRound);

                List<Vote> votes = new ArrayList<>();
                List<Player> eliminated = new ArrayList<>();

                if (twist.equalsIgnoreCase("LUCKY_NO_ELIMINATION")) {
                    System.out.println("\n[HOST TWIST] Tonight is a LUCKY_NO_ELIMINATION round! You are all safe. There will be no Tribal Council vote tonight!");
                } else {
                    if (twist.equalsIgnoreCase("DOUBLE_VOTE")) {
                        System.out.println("\n[HOST TWIST] Tonight is a DOUBLE_VOTE round! The immunity winner will cast two votes!");
                    } else if (twist.equalsIgnoreCase("LOSER_NO_VOTE")) {
                        System.out.println("\n[HOST TWIST] Tonight is a LOSER_NO_VOTE round! The contestant in last place cannot vote tonight!");
                    }

                    // 6. Tribal council strategy discussions (voting strategy secret messages)
                    System.out.println("\n--- Voting Strategy Conversations ---");
                    tribalCouncilPhase.runStrategyConversations(mergedTeam, mergeResult.immunityWinner(), mergeCampContext);

                    // 7. Cast votes
                    votes = tribalCouncilPhase.castMergeVotes(mergedTeam.getPlayers(), mergeResult.immunityWinner(), twist, mergeResult.challengeLoser());

                    // 8. Process eliminations
                    eliminated = tribalCouncilPhase.processMergeVotes(mergedTeam, votes, mergeResult.immunityWinner(), twist);
                }

                // Record who got eliminated for the next round's global news
                lastEliminated = eliminated;

                gameContext.checkpoint("Merged Tribal Council Complete");

                mergeRound++;
                round++;
            }

            // ========== THE FINAL SHOWDOWN (2 Players Left) ==========
            if (gameContext.getActivePlayers().size() == 2) {
                System.out.println("\n==================================================");
                System.out.println(">>> THE FINAL SHOWDOWN <<<");
                System.out.println("==================================================");
                Challenge finalChallenge = hostAgent.createChallenge(true);
                Map<String, String> finalPitches = challengePhase.runIndividualChallenge(gameContext.getActivePlayers(), finalChallenge);
                MergeChallengeResult finalResult = hostAgent.rankIndividualChallenge(gameContext.getActivePlayers(), finalPitches, finalChallenge);

                Player finalistA = finalResult.immunityWinner();
                Player finalistB = finalResult.challengeLoser();
                System.out.println("[HOST] " + finalistA.getName() + " won the Final Challenge and will present their case to the jury first!");

                // The Sole Survivor is no longer decided by the challenge alone —
                // the jury of everyone voted out after the merge now casts the
                // deciding vote between the two finalists, just like a real
                // Final Tribal Council. The Final Challenge winner only breaks
                // a tied jury.
                Player winner = tribalCouncilPhase.runJuryVote(gameContext.getJury(), finalistA, finalistB, finalistA);
                Player runnerUp = winner.getId().equals(finalistA.getId()) ? finalistB : finalistA;

                runnerUp.setEliminated(true);
                mergedTeam.removePlayer(runnerUp);

                List<Player> active = new ArrayList<>(gameContext.getActivePlayers());
                active.remove(runnerUp);
                gameContext.setActivePlayers(active);

                System.out.println("[ELIMINATED] " + runnerUp.getName() + " came up short in the jury's Final Tribal Council vote.");
            }

            System.out.println("\n==================================================");
            System.out.println("*** SOLE SURVIVOR: " + gameContext.getActivePlayers().get(0).getName() + " HAS OUTLASTED EVERYONE! ***");
            System.out.println("==================================================");

            gameContext.setWinnerName(gameContext.getActivePlayers().get(0).getName());
            // Completion is terminal: do not enter another step-by-step checkpoint.
            gameContext.setCurrentPhase("Game Over - Winner Crowned");
            gameContext.setStatus("COMPLETED");

        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                gameContext.setStatus("CANCELLED");
                System.out.println("[CANCELLED] Simulation stopped.");
            } else {
                gameContext.setStatus("FAILED");
                System.out.println("[FAILED] " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    private String determineTwist(int round) {
        // Cycle twists each round
        if (round == 1) return "NORMAL";
        if (round == 2) return "LUCKY_NO_ELIMINATION";
        return "DOUBLE_ELIMINATION";
    }

    private String determineMergeTwist(int round) {
        // Keep the opening twists, then resume normal elimination rounds.
        // An endless no-elimination tail prevents larger casts reaching the finale.
        if (round == 1) return "NORMAL";
        if (round == 2) return "DOUBLE_VOTE";
        if (round == 3) return "LOSER_NO_VOTE";
        return "NORMAL";
    }
}
