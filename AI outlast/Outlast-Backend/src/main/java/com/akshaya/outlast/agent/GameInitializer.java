package com.akshaya.outlast.agent;

import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.context.GameContext;
import com.akshaya.outlast.model.Player;
import com.akshaya.outlast.model.Host;
import com.akshaya.outlast.model.PlayerInitRequest;
import com.akshaya.outlast.utils.Gender;
import com.akshaya.outlast.utils.PersonalityType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GameInitializer {

    private final GameConfig gameConfig;
    private final PlayerFactory playerFactory;
    private final GameContext gameContext;

    public GameInitializer(GameConfig gameConfig, PlayerFactory playerFactory, GameContext gameContext) {
        this.gameConfig = gameConfig;
        this.playerFactory = playerFactory;
        this.gameContext = gameContext;
    }

    public List<Player> initializePlayers(List<PlayerInitRequest> requests) {
        List<Player> activePlayers = new ArrayList<>();

        // 1. Get the list of available models from config and shuffle them
        List<String> modelPool = gameConfig.availableModels();
        if (modelPool == null || modelPool.isEmpty()) {
            throw new IllegalStateException("No available models configured in game properties.");
        }
        List<String> shuffledModels = new ArrayList<>(modelPool);
        Collections.shuffle(shuffledModels);

        // 2. Load and shuffle all contestant personalities
        List<PersonalityType> personalityPool = new ArrayList<>(Arrays.asList(PersonalityType.values()));
        personalityPool.remove(PersonalityType.HOST); // Host is a moderator, not a player
        Collections.shuffle(personalityPool);

        for (int i = 0; i < requests.size(); i++) {
            PlayerInitRequest req = requests.get(i);
            
            // Pick a model from the shuffled pool in round-robin fashion
            String assignedModel = shuffledModels.get(i % shuffledModels.size());

            // Pick a personality (if we run out of unique personalities, cycle/wrap around)
            PersonalityType personality = personalityPool.get(i % personalityPool.size());

            // Instantiate and query LLM for starting stats
            Player player = playerFactory.createPlayer(
                String.valueOf(i + 1),
                req.name(),
                assignedModel,
                req.gender(),
                personality
            );

            activePlayers.add(player);

            System.out.println("Initialized: " + req.name() 
                               + " | Model: " + assignedModel 
                               + " | Personality: " + personality.getArchetype() 
                               + " | Stats: " + player.getStats()
                               + " | Intro: \"" + player.getIntroduction() + "\"");
        }

        // Save active players to GameContext
        gameContext.setActivePlayers(activePlayers);

      /*  // Initialize and save Host to GameContext
        Host host = new Host("The Host", gameConfig.hostModel(), PersonalityType.HOST);
        gameContext.setHost(host);*/

        return activePlayers;
    }
}
