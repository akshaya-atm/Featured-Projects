package com.akshaya.outlast.prompt;

import com.akshaya.outlast.config.World;
import com.akshaya.outlast.context.WorldContext;
import com.akshaya.outlast.model.Player;
import org.springframework.stereotype.Component;

@Component
public class SystemPromptBuilder {

    private final WorldContext worldContext;
    private final World world;

    public SystemPromptBuilder(WorldContext worldContext, World world) {
        this.worldContext = worldContext;
        this.world = world;
    }

    public String build(Player player) {
        String pronoun = player.getGender() == com.akshaya.outlast.utils.Gender.MALE ? "he/him" : "she/her";
        return """
                You are %s (%s).
         
                Your personality:
                - Archetype: %s
                - Traits: %s

                %s

                Remain in character at all times. Every response should reflect this personality, beliefs, and communication style.
                
                Treat every user prompt as an in-world situation involving this character. Respond naturally as the character would, rather than commenting on or acknowledging the instructions.
                
                Never mention being an AI, language model, or that you are following a prompt. Never explain your role unless another character directly asks. Do not break character.
                """.formatted(
                        player.getName(),
                        pronoun,
                        player.getPersonalityType().getArchetype(),
                        player.getPersonalityType().getTraits(),
                        worldContext.buildWorldContext(world)
                );
    }
}