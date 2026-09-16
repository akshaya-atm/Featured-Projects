package com.akshaya.outlast.agent;

import com.akshaya.outlast.client.LLMClient;
import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.config.World;
import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.SecretMessage;
import com.akshaya.outlast.utils.IntroMessage;
import com.akshaya.outlast.utils.JsonUtils;
import com.akshaya.outlast.utils.PromptMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CampLifePhase {
    private final LLMClient llmClient;
    private final GameConfig gameConfig;
    private final World world;
    private final com.akshaya.outlast.context.GameContext gameContext;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
            .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    public CampLifePhase(LLMClient llmClient, GameConfig gameConfig, World world, com.akshaya.outlast.context.GameContext gameContext) {
        this.llmClient = llmClient;
        this.gameConfig = gameConfig;
        this.world = world;
        this.gameContext = gameContext;
    }

    /**
     * Reusable — each player broadcasts a short intro to the group.
     * Works for team camp life AND team merge.
     *
     * @param players   the group of players introducing themselves
     * @param groupName label for context (e.g. "Team Alpha" or "The Merged Tribe")
     */
    public List<IntroMessage> collectIntros(List<Player> players, String groupName) {
        List<IntroMessage> introMessages = new ArrayList<>();

        for (Player player : players) {
            // ---- [TEST MODE - commented out] ----
            // String introText = "Hi, I'm " + player.getName() + ". I am a " + player.getPersonalityType().getArchetype()
            //         + " and I am looking forward to surviving and working with you all in " + groupName + ".";
            // ---- END TEST MODE ----

            String introPrompt = "You have just arrived at camp with your new teammates in " + groupName + ". "
                    + "Introduce yourself to the group in ONE short sentence (15 WORDS MAX — a hard ceiling, not a target. Shorter is better). Stay true to your personality. Be natural and in-character.";

            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", player.getSystemPrompt()));
            messages.add(new PromptMessage("user", introPrompt));

            String introText;
            try {
                introText = llmClient.chat(player.getModelId(), messages);
                introText = introText.replaceAll("(?s)<think>.*?</think>", "").trim();
            } catch (Exception e) {
                introText = "Hi, I'm " + player.getName() + ". Looking forward to working with everyone here in " + groupName + ".";
                System.out.println("[WARN] Intro LLM call failed for " + player.getName() + ", using fallback.");
            }

            introMessages.add(new IntroMessage(player, introText));
            System.out.println("[INTRO] " + player.getName() + ": " + introText);
        }

        return introMessages;
    }

    /**
     * Reusable — each player sends a secret message to ONE person (or stays silent).
     * Recipients then reply. Works for camp life, merge, tribal council — any phase.
     *
     * @param players      the group of players participating
     * @param roundContext formatted string of what happened this round (intros, challenge results, vote, etc.)
     */
    public List<SecretMessage> runSecretMessages(List<Player> players, String roundContext) {
        return runSecretMessages(players, roundContext, "Use this to make allies, manipulate rivals, or gather intel to advance your game.");
    }

    public List<SecretMessage> runSecretMessages(List<Player> players, String roundContext, String customInstructions) {
        String phaseDesc = getPhaseDesc();

        // Filter out eliminated players from active team roster
        List<Player> activeTeamPlayers = players.stream()
                .filter(p -> !p.isEliminated())
                .collect(Collectors.toList());

        // Build player lookup map for quick ID → Player resolution
        Map<String, Player> playerMap = new HashMap<>();
        for (Player p : activeTeamPlayers) {
            playerMap.put(p.getId(), p);
        }

        // ---- Step 1: Each player picks ONE person and sends a secret message ----
        List<PendingSecret> pendingSecrets = new ArrayList<>();

        for (Player sender : activeTeamPlayers) {
            // Build teammate list (active teammates except sender)
            StringBuilder teammateList = new StringBuilder();
            for (Player p : activeTeamPlayers) {
                if (!p.getId().equals(sender.getId())) {
                    teammateList.append("- ").append(p.getName())
                            .append(" (ID: ").append(p.getId()).append(")")
                            .append(" — ").append(p.getPersonalityType().getArchetype())
                            .append("\n");
                }
            }

            String relationsSummary = sender.getRelationsSummary();
            String gameSummary = sender.getGameSummary() != null && !sender.getGameSummary().isBlank()
                    ? sender.getGameSummary()
                    : "No events yet.";
            String msgLogSummary = sender.getMessageLogSummary();

            String endgameThreatPrompt = "";
            int activePlayersCount = gameContext.getActivePlayers().size();
            int socialIntel = sender.getStat(com.akshaya.outlast.model.StatType.SOCIAL_INTELLIGENCE);
            if (activePlayersCount <= 5 && socialIntel >= 75) {
                StringBuilder threatReport = new StringBuilder("\n=== Endgame Strategic Report ===\n");
                threatReport.append("You are in the final stretch of the game. Based on your high Social Intelligence, you have estimated the jury popularity and threat level of the remaining contestants:\n");
                
                List<Player> activePlayers = gameContext.getActivePlayers();
                for (Player p : activePlayers) {
                    if (p.isEliminated()) continue;
                    double threatScore = calculateJuryThreat(p);
                    String threatLevel = threatScore >= 75 ? "High" : (threatScore >= 45 ? "Moderate" : "Low");
                    threatReport.append("- ").append(p.getName()).append(" (ID: ").append(p.getId()).append(") — Jury Threat: ").append((int)threatScore).append(" (").append(threatLevel).append(")\n");
                }
                
                // Find closest confirmed ally
                com.akshaya.outlast.model.PlayerRelation closestAllyRel = null;
                int maxTrust = -1;
                for (com.akshaya.outlast.model.PlayerRelation rel : sender.getRelations().values()) {
                    if (rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED && !rel.isTargetEliminated()) {
                        int trust = rel.getTrustScore();
                        if (trust > maxTrust) {
                            maxTrust = trust;
                            closestAllyRel = rel;
                        }
                    }
                }
                if (closestAllyRel != null && maxTrust > 60) {
                    Player closestAlly = gameContext.getPlayerById(closestAllyRel.getId());
                    if (closestAlly != null && !closestAlly.isEliminated()) {
                        double selfThreat = calculateJuryThreat(sender);
                        double allyThreat = calculateJuryThreat(closestAlly);
                        if (allyThreat > selfThreat + 15) {
                            threatReport.append("\n*Strategic Warning*: ").append(closestAlly.getName()).append(" is your closest confirmed ally, but they are a massive threat to win the final jury vote over you. If you take them to the end, they will likely beat you. Consider cutting them (voting them out) tonight.\n");
                        }
                    }
                }
                threatReport.append("================================\n");
                endgameThreatPrompt = threatReport.toString();
            }

            List<PromptMessage> messages = new ArrayList<>();
            messages.add(new PromptMessage("system", sender.getSystemPrompt()));
            messages.add(new PromptMessage("user", """
                    Current Phase: %s
                    
                    Here's what happened:
                    %s
                    
                    Your personal game summary so far:
                    %s
                    
                    Your active teammates:
                    %s
                    Your social read on them:
                    %s
                    
                    Your conversation history (messages sent/received):
                    %s
                    
                    %s
                    
                    First, reflect on these events in character. 
                    Update your internal game summary, trust scores (0-100), brief notes on players, and alliance statuses:
                    - If someone accepted your alliance proposal or you accepted theirs, update alliance to CONFIRMED.
                    - If someone reached out but you/they did not confirm, it could be PROPOSED or NONE.
                    - Adjust trust scores according to how reliable they seem.
                    
                    STRATEGY TIP: If you have low Trustworthiness/Loyalty, or if you are a Chaotic provocateur / Machiavellian archetype, or if your confirmed ally is becoming a dangerous jury threat, you can choose your CONFIRMED ALLY as your trueTarget while sending them a deceptiveMessage reassuring them to catch them off-guard.
                    
                    You may also send ONE secret message to an active teammate. No one else will see it.
                    %s
                    You may also stay silent and observe (set to_id to null).
                    Stay true to your character. %d words max — a hard ceiling, not a target. Shorter is better.
                    
                    Respond with ONLY valid JSON (CRITICAL RULES: DO NOT target yourself (you are Contestant_%s); NO single-line // or multi-line /* */ comments, NO trailing commas before closing braces/brackets, NO markdown text outside the JSON object):
                    {
                      "to_id": "active_teammate_ID_from_the_list_above_or_null (MUST NOT BE YOUR OWN ID)",
                      "trueTarget": "active_teammate_ID_you_actually_intend_to_vote_out_tonight (MUST NOT BE YOUR OWN ID)",
                      "backupTarget": "active_teammate_ID_you_would_vote_out_as_backup (MUST NOT BE YOUR OWN ID)",
                      "trueReasoning": "private strategic reasoning for your targets (not sent)",
                      "honestMessage": "honest secret message to send if you are telling the truth",
                      "deceptiveMessage": "deceptive secret message to send if you are misdirecting them",
                      "alliance": true/false_if_proposing_alliance,
                      "trustUpdates": {
                        "teammate_ID": new_score_integer
                      },
                      "notesUpdates": {
                        "teammate_ID": "brief updated notes on teammate behavior"
                      },
                      "allianceUpdates": {
                        "teammate_ID": "NONE" or "PROPOSED" or "CONFIRMED" or "BROKEN"
                      },
                      "gameSummary": "concise updated summary of your standing and plans (max %d sentences)"
                    }
                    """.formatted(
                    phaseDesc, roundContext, gameSummary, teammateList, relationsSummary, msgLogSummary,
                    endgameThreatPrompt,
                    customInstructions, gameConfig.limits().maxSecretMessageWords(), sender.getId(),
                    gameConfig.limits().maxReflectionSummarySentences()
            )));

            String rawResponse = stripThinking(llmClient.chat(sender.getModelId(), messages));
            
            // 1. Process and apply memory reflections
            applyReflectionUpdates(rawResponse, sender);

            // 2. Parse messaging decision
            PendingSecret pending = parseSendDecision(rawResponse, sender, playerMap);
            pendingSecrets.add(pending);

            // Cache voting targets on the player model
            sender.setTrueTarget(pending.trueTarget);
            sender.setBackupTarget(pending.backupTarget);
            sender.setTrueReasoning(pending.trueReasoning);

            System.out.println("--------------------------------------------------------------------------------");
            System.out.println("[INTENT] " + sender.getName() + " (" + sender.getPersonalityType().getArchetype() + "):");
            System.out.println("  - True Target:   " + (pending.trueTarget != null ? "Contestant_" + pending.trueTarget : "None"));
            System.out.println("  - Backup Target: " + (pending.backupTarget != null ? "Contestant_" + pending.backupTarget : "None"));
            System.out.println("  - Tactical Plan: " + pending.trueReasoning);
            
            if (pending.recipient != null && pending.message != null && !pending.message.trim().isEmpty()) {
                String msgType = pending.isDeceptive ? "[DECEPTIVE]" : "[HONEST]";
                System.out.println("  - Secret Msg to Contestant_" + pending.recipient.getId() + ": \"" + pending.message.trim() + "\" " + msgType + (pending.allianceProposed ? " [ALLIANCE PROPOSED]" : ""));
            } else {
                System.out.println("  - Secret Msg:    (stayed silent)");
            }
            System.out.println("--------------------------------------------------------------------------------");
        }

        // Log messages in players' histories for next rounds
        for (PendingSecret ps : pendingSecrets) {
            if (ps.recipient != null && ps.message != null && !ps.message.trim().isEmpty()) {
                String sentLog = "To " + ps.recipient.getName() + " (ID: " + ps.recipient.getId() + "): \"" + ps.message.trim() + "\"" + (ps.allianceProposed ? " [ALLIANCE PROPOSED]" : "");
                String recvLog = "From " + ps.sender.getName() + " (ID: " + ps.sender.getId() + "): \"" + ps.message.trim() + "\"" + (ps.allianceProposed ? " [ALLIANCE PROPOSED]" : "");
                ps.sender.logMessage(sentLog);
                ps.recipient.logMessage(recvLog);
            }
        }

        // ---- Step 2: Package all results into SecretMessage records (One-way messages only) ----
        List<SecretMessage> results = new ArrayList<>();
        for (PendingSecret ps : pendingSecrets) {
            if (ps.recipient == null || ps.message == null || ps.message.trim().isEmpty()) {
                results.add(new SecretMessage(ps.sender, null, null, null, false, ps.trueTarget, ps.backupTarget, ps.trueReasoning, ps.isDeceptive));
            } else {
                results.add(new SecretMessage(
                        ps.sender, ps.recipient,
                        ps.message.trim(), null, // No instant reply
                        ps.allianceProposed,
                        ps.trueTarget, ps.backupTarget, ps.trueReasoning,
                        ps.isDeceptive
                ));
            }
        }

        return results;
    }

    void applyReflectionUpdates(String rawResponse, Player player) {
        try {
            String cleaned = rawResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
            cleaned = JsonUtils.extractJson(cleaned);
            JsonNode node = objectMapper.readTree(cleaned);

            // Update Game Summary
            if (node.has("gameSummary")) {
                player.setGameSummary(node.get("gameSummary").asText());
                System.out.println("[SUMMARY] " + player.getName() + ": " + player.getGameSummary());
            }

            // Update Trust Scores
            if (node.has("trustUpdates")) {
                JsonNode trustNode = node.get("trustUpdates");
                trustNode.fields().forEachRemaining(entry -> {
                    String targetId = entry.getKey();
                    if (targetId.toLowerCase().startsWith("contestant_")) {
                        targetId = targetId.substring("contestant_".length());
                    }
                    int trust = entry.getValue().asInt();
                    int oldTrust = player.getTrustScore(targetId);
                    player.setTrustScore(targetId, trust);
                    if (oldTrust != trust) {
                        System.out.println("[REFLECT - TRUST] " + player.getName() + " trust score for Contestant_" + targetId + ": " + oldTrust + " -> " + trust);
                    }
                });
            }

            // Update Notes
            if (node.has("notesUpdates")) {
                JsonNode notesNode = node.get("notesUpdates");
                notesNode.fields().forEachRemaining(entry -> {
                    String targetId = entry.getKey();
                    if (targetId.toLowerCase().startsWith("contestant_")) {
                        targetId = targetId.substring("contestant_".length());
                    }
                    String noteText = entry.getValue().asText();
                    com.akshaya.outlast.model.PlayerRelation rel = player.getRelation(targetId);
                    if (rel != null) {
                        String oldNote = rel.getNotes();
                        rel.setNotes(noteText);
                        if (oldNote == null || !oldNote.equalsIgnoreCase(noteText)) {
                            System.out.println("[REFLECT - NOTES] " + player.getName() + " note for Contestant_" + targetId + ": \"" + noteText + "\"");
                        }
                    }
                });
            }

            // Update Alliance
            if (node.has("allianceUpdates")) {
                JsonNode allianceNode = node.get("allianceUpdates");
                allianceNode.fields().forEachRemaining(entry -> {
                    String targetId = entry.getKey();
                    if (targetId.toLowerCase().startsWith("contestant_")) {
                        targetId = targetId.substring("contestant_".length());
                    }
                    try {
                        com.akshaya.outlast.model.AllianceStatus status = com.akshaya.outlast.model.AllianceStatus.valueOf(entry.getValue().asText().toUpperCase());
                        com.akshaya.outlast.model.PlayerRelation rel = player.getRelation(targetId);
                        if (rel != null) {
                            com.akshaya.outlast.model.AllianceStatus oldStatus = rel.getAlliance();
                            rel.setAlliance(status);
                            if (oldStatus != status) {
                                System.out.println("[REFLECT - ALLIANCE] " + player.getName() + " alliance status for Contestant_" + targetId + ": " + oldStatus + " -> " + status);
                            }
                        }
                    } catch (Exception e) {
                        // Ignore invalid enum values
                    }
                });
            }

            System.out.println("[REFLECT] " + player.getName() + " updated relations:\n" + player.getRelationsSummary() + "\n");

        } catch (Exception e) {
            System.out.println("[WARN] Failed to parse reflection JSON for " + player.getName() + ": " + e.getMessage());
        }
    }

    // ---- Private helpers ----

    private String getPhaseDesc() {
        return world.phases() != null
                ? world.phases().getOrDefault("camp_life", "Camp Life — teammates get to know each other.")
                : "Camp Life — teammates get to know each other.";
    }

    private PendingSecret parseSendDecision(String rawResponse, Player sender, Map<String, Player> playerMap) {
        try {
            String cleaned = JsonUtils.extractJson(rawResponse);
            JsonNode node = objectMapper.readTree(cleaned);

            // A silent player still has a private voting plan.
            String toId = node.hasNonNull("to_id") ? node.get("to_id").asText().trim() : "";
            boolean alliance = node.has("alliance") && node.get("alliance").asBoolean();

            Player recipient = playerMap.get(toId);
            if (recipient == null && toId.toLowerCase().startsWith("contestant_")) {
                String numericId = toId.substring("contestant_".length());
                recipient = playerMap.get(numericId);
            }

            if (recipient == null || recipient.isEliminated() || recipient.getId().equals(sender.getId())) {
                if (recipient != null && recipient.getId().equals(sender.getId())) {
                    System.out.println("[WARN] " + sender.getName() + " attempted to send secret message to self — treating as silent.");
                } else if (recipient != null && recipient.isEliminated()) {
                    System.out.println("[WARN] " + sender.getName() + " attempted to message eliminated player " + recipient.getName() + " — treating as silent.");
                } else if (!toId.isBlank() && !toId.equalsIgnoreCase("null")) {
                    System.out.println("[WARN] " + sender.getName() + " tried to message unknown ID: " + toId + ", treating as silent.");
                }
                recipient = null;
            }

            // Parse targets
            String rawTrueTarget = node.has("trueTarget") ? node.get("trueTarget").asText().trim() : null;
            if (rawTrueTarget != null && rawTrueTarget.toLowerCase().startsWith("contestant_")) {
                rawTrueTarget = rawTrueTarget.substring("contestant_".length());
            }
            Player trueTargetPlayer = playerMap.get(rawTrueTarget);
            if (trueTargetPlayer == null && rawTrueTarget != null) {
                String numericId = rawTrueTarget.replaceAll("\\D+", "");
                trueTargetPlayer = playerMap.get(numericId);
            }

            // Reject self-vote or eliminated trueTarget
            if (trueTargetPlayer != null && (trueTargetPlayer.getId().equals(sender.getId()) || trueTargetPlayer.isEliminated())) {
                System.out.println("[WARN] " + sender.getName() + " attempted to target self (" + trueTargetPlayer.getName() + ") or eliminated player — rerouting trueTarget.");
                trueTargetPlayer = null;
            }

            if (trueTargetPlayer == null) {
                // Find lowest-trust active non-self player
                int minTrust = Integer.MAX_VALUE;
                for (Player p : playerMap.values()) {
                    if (!p.getId().equals(sender.getId()) && !p.isEliminated()) {
                        int t = sender.getTrustScore(p.getId());
                        if (t < minTrust) {
                            minTrust = t;
                            trueTargetPlayer = p;
                        }
                    }
                }
            }
            String trueTarget = trueTargetPlayer != null ? trueTargetPlayer.getId() : null;

            String rawBackupTarget = node.has("backupTarget") ? node.get("backupTarget").asText().trim() : null;
            if (rawBackupTarget != null && rawBackupTarget.toLowerCase().startsWith("contestant_")) {
                rawBackupTarget = rawBackupTarget.substring("contestant_".length());
            }
            Player backupTargetPlayer = playerMap.get(rawBackupTarget);
            if (backupTargetPlayer == null && rawBackupTarget != null) {
                String numericId = rawBackupTarget.replaceAll("\\D+", "");
                backupTargetPlayer = playerMap.get(numericId);
            }

            // Reject self-vote, eliminated, or duplicate backupTarget
            if (backupTargetPlayer != null && (backupTargetPlayer.getId().equals(sender.getId()) || backupTargetPlayer.isEliminated() || (trueTargetPlayer != null && backupTargetPlayer.getId().equals(trueTargetPlayer.getId())))) {
                backupTargetPlayer = null;
            }

            if (backupTargetPlayer == null) {
                int secondMinTrust = Integer.MAX_VALUE;
                for (Player p : playerMap.values()) {
                    if (!p.getId().equals(sender.getId()) && !p.isEliminated() && (trueTargetPlayer == null || !p.getId().equals(trueTargetPlayer.getId()))) {
                        int t = sender.getTrustScore(p.getId());
                        if (t < secondMinTrust) {
                            secondMinTrust = t;
                            backupTargetPlayer = p;
                        }
                    }
                }
            }
            String backupTarget = backupTargetPlayer != null ? backupTargetPlayer.getId() : null;

            String trueReasoning = node.has("trueReasoning") ? node.get("trueReasoning").asText() : "No reasoning provided.";

            // Messages
            String honestMsg = node.has("honestMessage") ? node.get("honestMessage").asText() : (node.has("message") ? node.get("message").asText() : "");
            String deceptiveMsg = node.has("deceptiveMessage") ? node.get("deceptiveMessage").asText() : honestMsg;

            // Deception roll
            int trustworthiness = sender.getStat(com.akshaya.outlast.model.StatType.TRUSTWORTHINESS);
            int assertiveness = sender.getStat(com.akshaya.outlast.model.StatType.ASSERTIVENESS);
            int cooperation = sender.getStat(com.akshaya.outlast.model.StatType.COOPERATION);
            String personality = sender.getPersonalityType().getArchetype();

            double prob = (100 - trustworthiness) * 0.5 
                    + assertiveness * 0.2 
                    + (personality.equalsIgnoreCase("Chaotic provocateur") ? 20.0 : 0.0) 
                    - cooperation * 0.1;
            int lieProbability = (int) Math.max(5, Math.min(85, prob));

            boolean isDeceptive = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < lieProbability;
            
            String finalMessage = isDeceptive ? deceptiveMsg : honestMsg;

            if (trueTargetPlayer != null) {
                com.akshaya.outlast.model.PlayerRelation rel = sender.getRelation(trueTargetPlayer.getId());
                if (rel != null && rel.getAlliance() == com.akshaya.outlast.model.AllianceStatus.CONFIRMED) {
                    System.out.println("[BETRAYAL PLANNED] " + sender.getName() + " is plotting to target their CONFIRMED ally " + trueTargetPlayer.getName() + "!");
                }
            }

            if (recipient != null && isDeceptive && !finalMessage.equalsIgnoreCase(honestMsg)) {
                // Find what target they named in deceptive message (just for log purposes)
                String liedTargetName = "someone else";
                for (Player p : playerMap.values()) {
                    if (trueTarget != null && !p.getId().equals(trueTarget) && (deceptiveMsg.toLowerCase().contains(p.getName().toLowerCase()) || deceptiveMsg.contains(p.getId()))) {
                        liedTargetName = p.getName();
                        break;
                    }
                }
                String trueTargetName = trueTargetPlayer != null ? trueTargetPlayer.getName() : "none";
                System.out.println("[DECEPTION] " + sender.getName() + " told " + recipient.getName() + " they'd vote " + liedTargetName + ", actually voting " + trueTargetName + ".");
            }

            return new PendingSecret(sender, recipient, finalMessage, alliance, trueTarget, backupTarget, trueReasoning, isDeceptive);
        } catch (Exception e) {
            System.out.println("[WARN] Failed to parse secret message JSON for " + sender.getName() + " (" + e.getMessage() + ") — treating as silent to prevent information leaks.");
            return new PendingSecret(sender, null, null, false, null, null, null, false);
        }
    }

    private static String stripThinking(String content) {
        if (content == null) return "";
        return content.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    public double calculateJuryThreat(Player target) {
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

    /** Internal holder for a secret message before the reply is collected. */
    private record PendingSecret(
            Player sender,
            Player recipient,
            String message,
            boolean allianceProposed,
            String trueTarget,
            String backupTarget,
            String trueReasoning,
            boolean isDeceptive
    ) {}
}

