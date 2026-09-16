package com.akshaya.outlast.context;

import com.akshaya.outlast.config.World;
import org.springframework.stereotype.Component;

@Component
public class WorldContext {
    public String buildWorldContext(World world) {

        return """
            World Context:

            Name: %s

            Location: %s

            Premise:
            %s

            Environment:
            Climate: %s
            Current Weather/Conditions: %s

            Terrain:
            %s

            Wildlife:
            %s

            Available Resources:
            %s

            Restrictions:
            %s

            Rules of the Game:
            %s

            Objective:
            %s
            """.formatted(
                world.name(),
                world.location(),
                world.premise(),
                world.environment().climate(),
                world.environment().weather(),
                String.join(", ", world.environment().terrain()),
                String.join(", ", world.environment().wildlife()),
                String.join(", ", world.resources()),
                String.join(", ", world.restrictions()),
                world.rules() != null ? String.join("\n", world.rules()) : "",
                world.objective()
        );
    }
}
