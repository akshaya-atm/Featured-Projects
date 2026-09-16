package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.*;
import com.akshaya.outlast.utils.PromptMessage;
import com.akshaya.outlast.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TribalCouncilPhase {

    private final LLMClient llmClient;
    private final GameConfig gameConfig;
    private final GameContext gameContext;
    private final CampLifePhase campLifePhase;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    private String lastTribalTally = "";
    public String getLastTribalTally() { return lastTribalTally; }
    public void reset() { lastTribalTally = ""; }

    public TribalCouncilPhase(LLMClient llmClient, GameConfig gameConfig, GameContext gameContext, CampLifePhase campLifePhase) {
        this.llmClient = llmClient;
        this.gameConfig = gameConfig;
        this.gameContext = gameContext;
        this.campLifePhase = campLifePhase;
    }

    public List<SecretMessage> runStrategyConversations(Team losingTeam, Player immunityWinner, String roundContext) {
        System.out.println("\n--- Losing Team (" + losingTeam.getTeamName() + ") Voting Strategy Discussions ---");
        
        StringBuilder contextBuilder = new StringBuilder();
        if (roundContext != null && !roundContext.isBlank()) {
            contextBuilder.append(roundContext).append("\n");
        }
        contextBuilder.append("Tribal Council is tonight. Someone from your team will be voted out.");
        if (immunityWinner != null) {
            contextBuilder.append(" ").append(immunityWinner.getName()).append(" (ID: ").append(immunityWinner.getId()).append(") has won INDIVIDUAL IMMUNITY and is SAFE from being voted out tonight.");
        } else {
            contextBuilder.append(" All members of your team are vulnerable to being voted out.");
        }
        contextBuilder.append(" Use this time to coordinate your votes, build voting blocs, or betray alliances to save yourself.");
        
        String votingInstructions = "Tribal Council is tonight. Just like in normal secret messages, you can build relationships, propose alliances (by setting 'alliance': true), and you MUST update your rolling trust scores/notes/gameSummary.";
        if (immunityWinner != null) {
            votingInstructions += " Note: " + immunityWinner.getName() + " is immune and cannot be voted for. Vulnerable players must coordinate, but if only two players are vulnerable, they will inevitably vote for each other, making the immune player the ultimate swing vote! Use this strategy talk to persuade the swing vote or negotiate safety.";
        }
        votingInstructions += " You must discuss who to vote out and explain WHY. You may also choose to remain silent if you want to avoid making a move.";
        
        List<SecretMessage> messages = campLifePhase.runSecretMessages(losingTeam.getPlayers(), contextBuilder.toString(), votingInstructions);
        return messages;
    }

    /**
     * Stage 2: Cast secret votes for elimination.
     */
    public List<Vote> castVotes(Team losingTeam) {
        System.out.println("\n--- TRIBAL COUNCIL: CASTING VOTES ---");
        List<Vote> votes = new ArrayList<>();
        List<Player> players = losingTeam.getPlayers();

        Map<String, Player> playerMap = new HashMap<>();
        for (Player p : players) {
            playerMap.put(p.getId(), p);
        }

        for (Player voter : players) {
            String targetId = voter.getTrueTarget();
            String reason = voter.getTrueReasoning();
            if (reason == null || reason.isBlank()) {
                reason = "Voting for tactical survival.";
            }

            // Fallback if target is invalid, self, or null
            if (targetId == null || targetId.equals(voter.getId()) || !playerMap.containsKey(targetId) || playerMap.get(targetId).isEliminated()) {
                if (targetId != null && targetId.equals(voter.getId())) {
                    System.out.println("[WARN] " + voter.getName() + " attempted self-vote — rerouting vote to lowest trust active target.");
                } else if (targetId != null && playerMap.containsKey(targetId) && playerMap.get(targetId).isEliminated()) {
                    System.out.println("[WARN] " + voter.getName() + " attempted vote on eliminated player " + playerMap.get(targetId).getName() + " — rerouting vote.");
                }
                String bestFallback = null;
                int minTrust = Integer.MAX_VALUE;
                for (Player p : players) {
                    if (!p.getId().equals(voter.getId()) && !p.isEliminated()) {
                        int trust = voter.getTrustScore(p.getId());
                        if (trust < minTrust) {
                            minTrust = trust;
                            bestFallback = p.getId();
                        }
                    }
                }
                targetId = bestFallback;
            }

            // --- Hedging Check ---
            List<com.akshaya.outlast.model.PlayerRelation> confirmedAllies = new ArrayList<>();
            for (com.akshaya.outlast.model.PlayerRelation rel : voter.getRelations().values()) {
                if (rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED && !rel.isTargetEliminated() && playerMap.containsKey(rel.getId())) {
                    confirmedAllies.add(rel);
                }
            }

            if (!confirmedAllies.isEmpty()) {
                int N = confirmedAllies.size();
                int M = 0;
                Player targetPlayer = playerMap.get(targetId);
                if (targetPlayer != null) {
                    for (com.akshaya.outlast.model.PlayerRelation rel : confirmedAllies) {
                        Player ally = playerMap.get(rel.getId());
                        if (ally != null) {
                            for (String logEntry : voter.getMessageLog()) {
                                if (logEntry.startsWith("From " + ally.getName()) &&
                                        (logEntry.toLowerCase().contains(targetPlayer.getName().toLowerCase()) ||
                                         logEntry.contains(targetPlayer.getId()))) {
                                    M++;
                                    break;
                                }
                            }
                        }
                    }
                }

                double agreementRatio = (double) M / N;
                if (agreementRatio < 0.50) {
                    boolean hedge = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 25;
                    if (hedge) {
                        String backupId = voter.getBackupTarget();
                        if (backupId != null && !backupId.equals(voter.getId()) && playerMap.containsKey(backupId)) {
                            System.out.println("[HEDGE] " + voter.getName() + " splits from presumed majority target due to uncertainty.");
                            targetId = backupId;
                        }
                    }
                }
            }

            // --- Endgame Strategic Cut Check ---
            int activePlayersCount = gameContext.getActivePlayers().size();
            int socialIntel = voter.getStat(com.akshaya.outlast.model.StatType.SOCIAL_INTELLIGENCE);
            if (activePlayersCount <= 5 && socialIntel >= 75) {
                com.akshaya.outlast.model.PlayerRelation closestAllyRel = null;
                int maxTrust = -1;
                for (com.akshaya.outlast.model.PlayerRelation rel : voter.getRelations().values()) {
                    if (rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED && !rel.isTargetEliminated() && playerMap.containsKey(rel.getId())) {
                        int trust = rel.getTrustScore();
                        if (trust > maxTrust) {
                            maxTrust = trust;
                            closestAllyRel = rel;
                        }
                    }
                }

                if (closestAllyRel != null && maxTrust > 60) {
                    Player closestAlly = playerMap.get(closestAllyRel.getId());
                    if (closestAlly != null && !closestAlly.getId().equals(voter.getId())) {
                        double voterThreat = calculateJuryThreat(voter);
                        double allyThreat = calculateJuryThreat(closestAlly);
                        System.out.println("[DEBUG - JURY THREAT] " + voter.getName() + " (Threat: " + String.format("%.1f", voterThreat) + ") evaluating ally " + closestAlly.getName() + " (Threat: " + String.format("%.1f", allyThreat) + ")");
                        if (allyThreat > voterThreat + 10) {
                            targetId = closestAlly.getId();
                            System.out.println("[STRATEGIC CUT] " + voter.getName() + " (SI: " + socialIntel + ") overrides target to cut high-threat ally " + closestAlly.getName() + "!");
                        }
                    }
                }
            }

            Player target = playerMap.get(targetId);
            
            com.akshaya.outlast.model.PlayerRelation relToTarget = voter.getRelation(targetId);
            if (relToTarget != null && relToTarget.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED) {
                System.out.println("[BETRAYAL EXECUTED] " + voter.getName() + " cast a vote against their CONFIRMED ally " + (target != null ? target.getName() : "ID:" + targetId) + "!");
            }

            votes.add(new Vote(voter, target, reason));
            System.out.println("[TRIBAL VOTE] " + voter.getName() + " cast a vote.");
        }
        return votes;
    }

    /**
     * Stage 3: Process votes, execute elminations based on the Host twist.
     */
    public List<Player> processVotes(Team losingTeam, List<Vote> votes, String twistType) {
        System.out.println("\n--- Host Reveals the Votes (Twist: " + twistType + ") ---");
        
        // Count votes
        Map<String, Integer> tallies = new HashMap<>();
        Map<String, String> idToNameMap = new HashMap<>();
        for (Vote v : votes) {
            if (v.target() == null) continue;
            String targetId = v.target().getId();
            tallies.put(targetId, tallies.getOrDefault(targetId, 0) + 1);
            idToNameMap.put(targetId, v.target().getName());
        }

        // Tally printing (anonymous reveal)
        System.out.println("[REVEAL] Anonymous Vote Counts:");
        StringBuilder tallyBuilder = new StringBuilder("At the last Tribal Council, the following votes were cast anonymously:\n");
        for (Map.Entry<String, Integer> entry : tallies.entrySet()) {
            String name = idToNameMap.get(entry.getKey());
            System.out.println("- " + name + " received " + entry.getValue() + " vote(s).");
            tallyBuilder.append("- ").append(name).append(" received ").append(entry.getValue()).append(" vote(s).\n");
        }
        this.lastTribalTally = tallyBuilder.toString();

        List<Player> eliminated = new ArrayList<>();

        if (twistType.equalsIgnoreCase("LUCKY_NO_ELIMINATION")) {
            System.out.println("[HOST TWIST] You are lucky! Today is a non-elimination round. No one is voted out.");
            return eliminated;
        }

        if (losingTeam.getPlayers().size() == 1 && tallies.isEmpty()) {
            Player lonePlayer = losingTeam.getPlayers().get(0);
            tallies.put(lonePlayer.getId(), 0);
            System.out.println("[HOST] " + lonePlayer.getName() + " is the last member of the losing tribe and is eliminated automatically.");
        }
        // Resolve tied team votes by fire-making, just as in merged council.
        // Shuffle first so an exact grit tie is not decided by ID/hash-map order.
        List<Map.Entry<String, Integer>> sortedTallies = new ArrayList<>(tallies.entrySet());
        Collections.shuffle(sortedTallies);
        sortedTallies.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparingInt(e -> gameContext.getPlayerById(e.getKey()).getStat(StatType.SURVIVAL_GRIT)));
        if (new HashSet<>(tallies.values()).size() < tallies.size()) {
            System.out.println("[TIE BREAK] Tied vote positions are decided by Survival Grit in fire-making; equal grit is a random draw.");
        }

        int countToEliminate = twistType.equalsIgnoreCase("DOUBLE_ELIMINATION") ? 2 : 1;
        
        for (int i = 0; i < countToEliminate && i < sortedTallies.size(); i++) {
            String idToEliminate = sortedTallies.get(i).getKey();
            Player playerToEliminate = gameContext.getPlayerById(idToEliminate);
            if (playerToEliminate != null) {
                eliminated.add(playerToEliminate);
            }
        }

        // Eliminate players
        for (Player p : eliminated) {
            p.setEliminated(true);
            losingTeam.removePlayer(p);
            
            // Remove from game context active list
            List<Player> active = new ArrayList<>(gameContext.getActivePlayers());
            active.remove(p);
            gameContext.setActivePlayers(active);

            System.out.println("[ELIMINATED] " + p.getName() + " has been voted out of the island!");
        }

        // Ensure Captain is still active
        ensureCaptainActive(losingTeam);

        return eliminated;
    }

    /**
     * Cast secret votes at Tribal Council during the Merge Phase.
     * Supports immunity protection, Double Vote twists, and Loser No Vote twist penalties.
     */
    public List<Vote> castMergeVotes(List<Player> players, Player immunityWinner, String twistType, Player challengeLoser) {
        System.out.println("\n--- MERGED TRIBAL COUNCIL: CASTING VOTES ---");
        List<Vote> votes = new ArrayList<>();

        Map<String, Player> playerMap = new HashMap<>();
        for (Player p : players) {
            playerMap.put(p.getId(), p);
        }

        for (Player voter : players) {
            // Check Loser No Vote Twist
            if (twistType.equalsIgnoreCase("LOSER_NO_VOTE") && challengeLoser != null && voter.getId().equals(challengeLoser.getId())) {
                System.out.println("[TRIBAL VOTE] " + voter.getName() + " has NO VOTE tonight (penalty for coming in last place).");
                continue;
            }

            boolean hasDoubleVote = twistType.equalsIgnoreCase("DOUBLE_VOTE") && immunityWinner != null && voter.getId().equals(immunityWinner.getId());

            String targetId = voter.getTrueTarget();
            String backupId = voter.getBackupTarget();
            String reason = voter.getTrueReasoning();
            if (reason == null || reason.isBlank()) {
                reason = "Voting for tactical survival.";
            }

            // Fallback if target is invalid, null, self, or immune
            if (targetId == null || targetId.equals(voter.getId()) || (immunityWinner != null && targetId.equals(immunityWinner.getId())) || !playerMap.containsKey(targetId) || playerMap.get(targetId).isEliminated()) {
                if (targetId != null && targetId.equals(voter.getId())) {
                    System.out.println("[WARN] " + voter.getName() + " attempted self-vote — rerouting vote to lowest trust active target.");
                } else if (targetId != null && immunityWinner != null && targetId.equals(immunityWinner.getId())) {
                    System.out.println("[WARN] " + voter.getName() + " attempted vote on immune player " + immunityWinner.getName() + " — rerouting vote.");
                } else if (targetId != null && playerMap.containsKey(targetId) && playerMap.get(targetId).isEliminated()) {
                    System.out.println("[WARN] " + voter.getName() + " attempted vote on eliminated player " + playerMap.get(targetId).getName() + " — rerouting vote.");
                }
                String bestFallback = null;
                int minTrust = Integer.MAX_VALUE;
                for (Player p : players) {
                    if (!p.getId().equals(voter.getId()) && !p.isEliminated() && (immunityWinner == null || !p.getId().equals(immunityWinner.getId()))) {
                        int trust = voter.getTrustScore(p.getId());
                        if (trust < minTrust) {
                            minTrust = trust;
                            bestFallback = p.getId();
                        }
                    }
                }
                targetId = bestFallback;
            }

            // --- Hedging Check ---
            List<com.akshaya.outlast.model.PlayerRelation> confirmedAllies = new ArrayList<>();
            for (com.akshaya.outlast.model.PlayerRelation rel : voter.getRelations().values()) {
                if (rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED && !rel.isTargetEliminated() && playerMap.containsKey(rel.getId())) {
                    confirmedAllies.add(rel);
                }
            }

            if (!confirmedAllies.isEmpty()) {
                int N = confirmedAllies.size();
                int M = 0;
                Player targetPlayer = playerMap.get(targetId);
                if (targetPlayer != null) {
                    for (com.akshaya.outlast.model.PlayerRelation rel : confirmedAllies) {
                        Player ally = playerMap.get(rel.getId());
                        if (ally != null) {
                            for (String logEntry : voter.getMessageLog()) {
                                if (logEntry.startsWith("From " + ally.getName()) &&
                                        (logEntry.toLowerCase().contains(targetPlayer.getName().toLowerCase()) ||
                                         logEntry.contains(targetPlayer.getId()))) {
                                    M++;
                                    break;
                                }
                            }
                        }
                    }
                }

                double agreementRatio = (double) M / N;
                if (agreementRatio < 0.50) {
                    boolean hedge = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < 25;
                    if (hedge) {
                        if (backupId != null && !backupId.equals(voter.getId()) && (immunityWinner == null || !backupId.equals(immunityWinner.getId())) && playerMap.containsKey(backupId)) {
                            System.out.println("[HEDGE] " + voter.getName() + " splits from presumed majority target due to uncertainty.");
                            targetId = backupId;
                        }
                    }
                }
            }

            // --- Endgame Strategic Cut Check ---
            int activePlayersCount = gameContext.getActivePlayers().size();
            int socialIntel = voter.getStat(com.akshaya.outlast.model.StatType.SOCIAL_INTELLIGENCE);
            if (activePlayersCount <= 5 && socialIntel >= 75) {
                com.akshaya.outlast.model.PlayerRelation closestAllyRel = null;
                int maxTrust = -1;
                for (com.akshaya.outlast.model.PlayerRelation rel : voter.getRelations().values()) {
                    if (rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED && !rel.isTargetEliminated() && playerMap.containsKey(rel.getId())) {
                        int trust = rel.getTrustScore();
                        if (trust > maxTrust) {
                            maxTrust = trust;
                            closestAllyRel = rel;
                        }
                    }
                }

                if (closestAllyRel != null && maxTrust > 60) {
                    Player closestAlly = playerMap.get(closestAllyRel.getId());
                    if (closestAlly != null && !closestAlly.getId().equals(voter.getId()) && (immunityWinner == null || !closestAlly.getId().equals(immunityWinner.getId()))) {
                        double voterThreat = calculateJuryThreat(voter);
                        double allyThreat = calculateJuryThreat(closestAlly);
                        System.out.println("[DEBUG - JURY THREAT] " + voter.getName() + " (Threat: " + String.format("%.1f", voterThreat) + ") evaluating ally " + closestAlly.getName() + " (Threat: " + String.format("%.1f", allyThreat) + ")");
                        if (allyThreat > voterThreat + 10) {
                            targetId = closestAlly.getId();
                            System.out.println("[STRATEGIC CUT] " + voter.getName() + " (SI: " + socialIntel + ") overrides target to cut high-threat ally " + closestAlly.getName() + "!");
                        }
                    }
                }
            }

            Player target = playerMap.get(targetId);

            com.akshaya.outlast.model.PlayerRelation relToTarget = voter.getRelation(targetId);
            if (relToTarget != null && relToTarget.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED) {
                System.out.println("[BETRAYAL EXECUTED] " + voter.getName() + " cast a vote against their CONFIRMED ally " + (target != null ? target.getName() : "ID:" + targetId) + "!");
            }
            
            if (hasDoubleVote) {
                votes.add(new Vote(voter, target, reason + " (First vote)"));
                
                // Second vote fallback to backup target
                Player secondTarget = target;
                if (backupId != null && !backupId.equals(voter.getId()) && (immunityWinner == null || !backupId.equals(immunityWinner.getId())) && playerMap.containsKey(backupId)) {
                    secondTarget = playerMap.get(backupId);
                }
                votes.add(new Vote(voter, secondTarget, reason + " (Second vote)"));
                System.out.println("[TRIBAL VOTE] " + voter.getName() + " cast two votes.");
            } else {
                votes.add(new Vote(voter, target, reason));
                System.out.println("[TRIBAL VOTE] " + voter.getName() + " cast a vote.");
            }
        }
        return votes;
    }

    /**
     * Process votes at Tribal Council during the Merge Phase.
     * Tallies votes anonymously and executes eliminations.
     */
    public List<Player> processMergeVotes(Team mergedTeam, List<Vote> votes, Player immunityWinner, String twistType) {
        System.out.println("\n--- Host Reveals the Merge Votes (Twist: " + twistType + ") ---");

        // Count votes
        Map<String, Integer> tallies = new HashMap<>();
        Map<String, String> idToNameMap = new HashMap<>();
        for (Vote v : votes) {
            if (v.target() == null) continue;
            String targetId = v.target().getId();
            tallies.put(targetId, tallies.getOrDefault(targetId, 0) + 1);
            idToNameMap.put(targetId, v.target().getName());
        }

        // Tally printing (anonymous reveal)
        // Tally printing (anonymous reveal)
        System.out.println("[REVEAL] Anonymous Vote Counts:");
        StringBuilder tallyBuilder = new StringBuilder("At the last Tribal Council, the following votes were cast anonymously:\n");
        for (Map.Entry<String, Integer> entry : tallies.entrySet()) {
            String name = idToNameMap.get(entry.getKey());
            System.out.println("- " + name + " received " + entry.getValue() + " vote(s).");
            tallyBuilder.append("- ").append(name).append(" received ").append(entry.getValue()).append(" vote(s).\n");
        }
        this.lastTribalTally = tallyBuilder.toString();

        List<Player> eliminated = new ArrayList<>();

        if (twistType.equalsIgnoreCase("LUCKY_NO_ELIMINATION")) {
            System.out.println("[HOST TWIST] You are lucky! Today is a non-elimination round. No one is voted out.");
            return eliminated;
        }

        // Sort players in merged tribe by vote tally descending
        List<Map.Entry<String, Integer>> sortedTallies = new ArrayList<>(tallies.entrySet());
        sortedTallies.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        if (!sortedTallies.isEmpty()) {
            int maxVotes = sortedTallies.get(0).getValue();
            List<Player> tiedPlayers = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : sortedTallies) {
                if (entry.getValue() == maxVotes) {
                    Player p = gameContext.getPlayerById(entry.getKey());
                    if (p != null) {
                        tiedPlayers.add(p);
                    }
                }
            }

            Player playerToEliminate;
            if (tiedPlayers.size() > 1) {
                StringBuilder tiedNames = new StringBuilder();
                for (int i = 0; i < tiedPlayers.size(); i++) {
                    tiedNames.append(tiedPlayers.get(i).getName());
                    if (i < tiedPlayers.size() - 1) tiedNames.append(", ");
                }
                System.out.println("\n[TIE] A voting tie has occurred between: " + tiedNames.toString() + " with " + maxVotes + " vote(s) each!");
                System.out.println("[TIE BREAK] Moving to a dramatic Fire-Making Challenge to break the tie!");

                // Sort by SURVIVAL_GRIT ascending (lowest grit first)
                Collections.shuffle(tiedPlayers);
                tiedPlayers.sort(Comparator.comparingInt(p -> p.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50)));

                // Player with lowest Survival Grit is eliminated
                playerToEliminate = tiedPlayers.get(0);
                Player winner = tiedPlayers.get(tiedPlayers.size() - 1);

                System.out.println("[TIE BREAK] " + winner.getName() + " (Survival Grit: "
                        + winner.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50)
                        + ") built their fire first and is SAFE!");
                System.out.println("[TIE BREAK] " + playerToEliminate.getName() + " (Survival Grit: "
                        + playerToEliminate.getStats().getOrDefault(StatType.SURVIVAL_GRIT, 50)
                        + ") failed to light their tinder in time.");
            } else {
                playerToEliminate = tiedPlayers.get(0);
            }

            if (playerToEliminate != null) {
                eliminated.add(playerToEliminate);
            }
        }

        // Eliminate players
        for (Player p : eliminated) {
            p.setEliminated(true);
            mergedTeam.removePlayer(p);

            // Remove from game context active list
            List<Player> active = new ArrayList<>(gameContext.getActivePlayers());
            active.remove(p);
            gameContext.setActivePlayers(active);

            // Post-merge boots join the jury that will crown the Sole Survivor.
            gameContext.addToJury(p);

            System.out.println("[ELIMINATED] " + p.getName() + " has been voted out of the island!");
        }

        return eliminated;
    }

    /**
     * Final Tribal Council: every juror (a post-merge eliminated player) casts
     * a vote for one of the two finalists based on that finalist's own
     * closing case (their latest self-reported gameSummary) and the juror's
     * personal memory of how the two finalists treated them. Majority wins;
     * a tie falls back to whichever finalist is passed as tieBreakWinner.
     */
    public Player runJuryVote(List<Player> jury, Player finalistA, Player finalistB, Player tieBreakWinner) {
        System.out.println("\n--- FINAL TRIBAL COUNCIL: THE JURY VOTES ---");

        if (jury == null || jury.isEmpty()) {
            Player winner = tieBreakWinner != null ? tieBreakWinner : finalistA;
            System.out.println("[HOST] There is no jury seated to vote — the Final Challenge alone decides it.");
            System.out.println("[JURY REVEAL] " + winner.getName() + " wins with no jury present!");
            return winner;
        }

        Map<String, Integer> tallies = new HashMap<>();
        tallies.put(finalistA.getId(), 0);
        tallies.put(finalistB.getId(), 0);

        for (Player juror : jury) {
            String jurorPrompt = """
                    You were voted out earlier and are now a member of the FINAL JURY.
                    Two finalists remain and are making their closing case for why they
                    deserve to win. It is your turn to cast the deciding vote for the
                    Sole Survivor.

                    Finalist A: %s (%s)
                    Their closing case: "%s"

                    Finalist B: %s (%s)
                    Their closing case: "%s"

                    Your own memory of the game, and how these two treated you specifically:
                    %s

                    Vote for whoever most deserves to win based on their overall game —
                    strategy, social play, and how they treated you and others. Stay true
                    to your personality and any grudges you're still holding.

                    Respond with ONLY valid JSON (no markdown, no comments):
                    {
                      "vote": "A_or_B",
                      "reasoning": "one short sentence explaining your vote (15 words max — a hard ceiling, not a target; shorter is better)"
                    }
                    """.formatted(
                    finalistA.getName(), finalistA.getPersonalityType().getArchetype(), finalistA.getGameSummary(),
                    finalistB.getName(), finalistB.getPersonalityType().getArchetype(), finalistB.getGameSummary(),
                    juror.getRelationsSummary()
            );

            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", juror.getSystemPrompt()));
            messages.add(new PromptMessage("user", jurorPrompt));

            int trustA = juror.getTrustScore(finalistA.getId());
            int trustB = juror.getTrustScore(finalistB.getId());
            Player votedFor = trustA == trustB
                    ? (java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? finalistA : finalistB)
                    : (trustA > trustB ? finalistA : finalistB);
            String reasoning = "Voting from my remembered trust in the finalists.";
            try {
                String raw = stripThinking(llmClient.chat(juror.getModelId(), messages));
                String cleaned = JsonUtils.extractJson(raw);
                JsonNode node = objectMapper.readTree(cleaned);
                String choice = node.path("vote").asText("").trim().toUpperCase();
                if (!choice.equals("A") && !choice.equals("B")) throw new IllegalArgumentException("Invalid jury choice");
                votedFor = choice.equals("B") ? finalistB : finalistA;
                if (node.has("reasoning")) {
                    reasoning = node.get("reasoning").asText(reasoning).trim();
                }
            } catch (Exception e) {
                System.out.println("[WARN] Jury vote LLM call failed for " + juror.getName() + ", defaulting vote.");
            }

            tallies.merge(votedFor.getId(), 1, Integer::sum);
            System.out.println("[JURY VOTE] " + juror.getName() + " votes for " + votedFor.getName() + ": \"" + reasoning + "\"");
        }

        int votesA = tallies.getOrDefault(finalistA.getId(), 0);
        int votesB = tallies.getOrDefault(finalistB.getId(), 0);
        System.out.println("[JURY REVEAL] " + finalistA.getName() + " received " + votesA + " vote(s).");
        System.out.println("[JURY REVEAL] " + finalistB.getName() + " received " + votesB + " vote(s).");

        if (votesA == votesB) {
            Player winner = tieBreakWinner != null ? tieBreakWinner : finalistA;
            System.out.println("[JURY REVEAL] The jury is deadlocked! " + winner.getName() + " wins on the strength of their Final Challenge performance.");
            return winner;
        }

        return votesA > votesB ? finalistA : finalistB;
    }

    private String normalizeTargetId(String targetId, String voterId, String immunityWinnerId, Map<String, Player> playerMap) {
        if (targetId.toLowerCase().startsWith("contestant_")) {
            targetId = targetId.substring("contestant_".length());
        }

        // Validate
        if (targetId.equals(voterId) || targetId.equals(immunityWinnerId) || !playerMap.containsKey(targetId)) {
            // Fallback: vote for random valid teammate
            List<String> validIds = new ArrayList<>(playerMap.keySet());
            validIds.remove(voterId);
            validIds.remove(immunityWinnerId);
            Collections.shuffle(validIds);
            return validIds.isEmpty() ? "" : validIds.get(0);
        }
        return targetId;
    }

    private void ensureCaptainActive(Team team) {
        if (team.getPlayers().isEmpty()) {
            return;
        }

        Player currentCaptain = team.getCaptain();
        if (currentCaptain == null || currentCaptain.isEliminated()) {
            // Pick new captain: highest trustworthiness stat
            Player newCaptain = team.getPlayers().stream()
                    .max(Comparator.comparingInt(p -> p.getStats().getOrDefault(StatType.TRUSTWORTHINESS, 50)))
                    .orElse(team.getPlayers().get(0));

            team.setCaptain(newCaptain);
            System.out.println("[NEW CAPTAIN] Captain " + (currentCaptain != null ? currentCaptain.getName() : "") 
                    + " was eliminated. " + newCaptain.getName() + " is the new Captain of " + team.getTeamName() + "!");
        }
    }

    // ---- Private Helpers ----

    private String parseJsonField(String rawResponse, String fieldName, String playerName) {
        try {
            String cleaned = JsonUtils.extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(cleaned);
            if (node.has(fieldName)) {
                return node.get(fieldName).asText();
            }
            if (fieldName.equals("vote_for_id") && node.has("vote")) {
                return node.get("vote").asText();
            }
            if (fieldName.equals("vote_for_id") && node.has("target")) {
                return node.get("target").asText();
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

    private double calculateJuryThreat(Player target) {
        int wins = target.getIndividualWins();
        int socialIntel = target.getStat(com.akshaya.outlast.model.StatType.SOCIAL_INTELLIGENCE);
        
        int trustCount = 0;
        for (Player other : gameContext.getActivePlayers()) {
            if (!other.getId().equals(target.getId())) {
                com.akshaya.outlast.model.PlayerRelation rel = other.getRelation(target.getId());
                if (rel != null && rel.getTrustScore() > 60) {
                    trustCount++;
                }
            }
        }
        
        return 0.4 * (wins * 10) + 0.3 * socialIntel + 0.3 * (trustCount * 25);
    }

    private static String stripThinking(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }
}
