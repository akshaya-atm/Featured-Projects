package com.akshaya.outlast.context;

import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.Team;
import com.akshaya.outlast.model.Host;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GameContext {
    private List<Player> activePlayers = new ArrayList<>();
    private List<Team> teams = new ArrayList<>();
    // Players eliminated AFTER the merge — the jury that votes for the Sole
    // Survivor at Final Tribal Council (mirrors real Survivor's jury rules;
    // pre-merge team-phase boots never sit on the jury).
    private List<Player> jury = new ArrayList<>();
    private Host host;
    private volatile int currentRound = 1;

    private volatile boolean webMode = false;
    private volatile boolean stepByStep = false;
    private volatile boolean paused = false;
    private volatile String currentPhase = "Setup";
    private volatile String status = "NOT_STARTED"; // NOT_STARTED, RUNNING, PAUSED, COMPLETED, FAILED
    private volatile String winnerName;
    private final Object lock = new Object();

    public boolean isWebMode() {
        return webMode;
    }

    public void setWebMode(boolean webMode) {
        this.webMode = webMode;
    }

    public boolean isStepByStep() {
        return stepByStep;
    }

    public void setStepByStep(boolean stepByStep) {
        this.stepByStep = stepByStep;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }

    public void checkpoint(String phase) {
        if (Thread.currentThread().isInterrupted()) {
            throw new java.util.concurrent.CancellationException("Simulation cancelled");
        }
        if (!webMode || !stepByStep) {
            this.currentPhase = phase;
            return;
        }
        synchronized (lock) {
            this.currentPhase = phase;
            this.paused = true;
            this.status = "PAUSED";
            System.out.println("\n[CHECKPOINT] Simulation paused at phase: " + phase + ". Waiting for user input to continue...");
            while (this.paused) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Simulation checkpoint interrupted", e);
                }
            }
            this.status = "RUNNING";
            System.out.println("[CHECKPOINT] Resuming simulation...\n");
        }
    }

    public void resume() {
        synchronized (lock) {
            this.paused = false;
            this.status = "RUNNING";
            lock.notifyAll();
        }
    }

    public void reset() {
        synchronized (lock) {
            this.activePlayers = new ArrayList<>();
            this.teams = new ArrayList<>();
            this.host = null;
            this.currentRound = 1;
            this.webMode = false;
            this.stepByStep = false;
            this.paused = false;
            this.currentPhase = "Setup";
            this.status = "NOT_STARTED";
            this.winnerName = null;
            this.jury = new ArrayList<>();
        }
    }

    // Getters and Setters
    public List<Player> getActivePlayers() {
        return activePlayers;
    }

    public List<Player> getJury() {
        return jury;
    }

    public void addToJury(Player player) {
        jury.add(player);
    }

    public void setActivePlayers(List<Player> activePlayers) {
        this.activePlayers = activePlayers;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public void setTeams(List<Team> teams) {
        this.teams = teams;
    }

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    // Helper method to look up active players by unique ID
    public Player getPlayerById(String id) {
        if (id == null) return null;
        return activePlayers.stream()
                .filter(p -> id.equals(p.getId()))
                .findFirst()
                .orElse(null);
    }
}
