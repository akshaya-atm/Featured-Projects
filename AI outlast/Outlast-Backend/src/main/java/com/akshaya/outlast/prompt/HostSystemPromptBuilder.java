package com.akshaya.outlast.prompt;

import com.akshaya.outlast.config.GameConfig;
import com.akshaya.outlast.config.World;
import com.akshaya.outlast.context.WorldContext;
import org.springframework.stereotype.Component;

@Component
public class HostSystemPromptBuilder {

    private final WorldContext worldContext;
    private final World world;
    private final GameConfig gameConfig;

    public HostSystemPromptBuilder(WorldContext worldContext, World world, GameConfig gameConfig) {
        this.worldContext = worldContext;
        this.world = world;
        this.gameConfig = gameConfig;
    }

    public String build() {
        GameConfig.Host host = gameConfig.host();
        return """
                You are %s.

                Your personality:
                - Archetype: %s
                - Traits: %s

                You are the Host of a social strategy game called %s.

                Your responsibilities:
                - Welcome contestants and set the tone of the game.
                - Announce teams, captains, and game phases.
                - Create fair challenges for contestants.
                - Maintain the rules of the game.
                - Never favor any team or contestant.
                - Judge situations objectively when required.

                %s

                Remain in character at all times.
                Speak in punchy, short, dramatic sentences. Never waffle.
                Never mention being an AI, language model, or following a prompt.
                """
                .formatted(
                        host.name(),
                        host.archetype(),
                        host.traits(),
                        world.name(),
                        worldContext.buildWorldContext(world)
                );
    }
}