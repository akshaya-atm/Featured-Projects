package com.akshaya.outlast.controller;

import com.akshaya.outlast.GameSimulationRunner;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.GameConfigOptions;
import com.akshaya.outlast.utils.GameLogService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameContext gameContext;
    private final GameSimulationRunner gameSimulationRunner;
    private final GameLogService gameLogService;
    private Thread simulationThread;

    public GameController(GameContext gameContext, GameSimulationRunner gameSimulationRunner, GameLogService gameLogService) {
        this.gameContext = gameContext;
        this.gameSimulationRunner = gameSimulationRunner;
        this.gameLogService = gameLogService;
    }

    @PostMapping("/start")
    public synchronized ResponseEntity<?> startGame(@RequestBody GameConfigOptions options) {
        try {
            if (options == null) throw new IllegalArgumentException("Game settings are required.");
            options.validate();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        if ((simulationThread != null && simulationThread.isAlive()) || "RUNNING".equalsIgnoreCase(gameContext.getStatus()) || "PAUSED".equalsIgnoreCase(gameContext.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A game simulation is already in progress."));
        }

        // Reset context and logs
        gameContext.reset();
        gameContext.setWebMode(true);
        gameContext.setStepByStep(options.isStepByStep());
        gameContext.setStatus("RUNNING");
        
        gameLogService.clear();
        gameLogService.startCapturing();

        // Run game simulation in background thread
        simulationThread = new Thread(() -> {
            try {
                System.out.println("[API] Starting AI Outlast Game Simulation...");
                gameSimulationRunner.runGame(options);
            } catch (Exception e) {
                gameContext.setStatus(Thread.currentThread().isInterrupted() ? "CANCELLED" : "FAILED");
                System.err.println("[API ERROR] Error in simulation thread: " + e.getMessage());
                e.printStackTrace();
            }
        }, "outlast-simulation");
        simulationThread.start();

        return ResponseEntity.ok(Map.of("message", "Game simulation started."));
    }

    @GetMapping("/state")
    public ResponseEntity<?> getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("status", gameContext.getStatus());
        state.put("currentRound", gameContext.getCurrentRound());
        state.put("currentPhase", gameContext.getCurrentPhase());
        state.put("activePlayers", gameContext.getActivePlayers());
        state.put("teams", gameContext.getTeams());
        state.put("winnerName", gameContext.getWinnerName());
        state.put("paused", gameContext.isPaused());
        state.put("stepByStep", gameContext.isStepByStep());
        return ResponseEntity.ok(state);
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(@RequestParam(value = "from", defaultValue = "0") int from) {
        List<String> logs = gameLogService.getLogs(from);
        return ResponseEntity.ok(Map.of(
            "logs", logs,
            "nextIndex", from + logs.size()
        ));
    }

    @PostMapping("/next")
    public synchronized ResponseEntity<?> nextPhase() {
        if ("STOPPING".equals(gameContext.getStatus())) {
            return ResponseEntity.status(409).body(Map.of("error", "The game is stopping."));
        }
        if (!gameContext.isPaused()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Game is not paused. Cannot advance."));
        }
        gameContext.resume();
        return ResponseEntity.ok(Map.of("message", "Signaled simulation to proceed."));
    }

    @PostMapping("/reset")
    public synchronized ResponseEntity<?> resetGame() {
        if (simulationThread != null && simulationThread.isAlive()) {
            gameContext.setStatus("STOPPING");
            simulationThread.interrupt(); // Also wakes a paused checkpoint.
            try {
                simulationThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.status(503).body(Map.of("error", "Reset interrupted. Please retry."));
            }
            if (simulationThread.isAlive()) {
                // Never clear a context while its old worker can still write to it.
                return ResponseEntity.status(409).body(Map.of("error", "The previous game is still stopping. Please retry reset in a moment."));
            }
        }
        simulationThread = null;
        gameContext.reset();
        gameLogService.clear();
        gameLogService.stopCapturing();
        System.out.println("[API] Game state reset.");
        return ResponseEntity.ok(Map.of("message", "Game state and logs have been reset."));
    }
}
