package com.akshaya.outlast.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "world")
public record World(
        String name,
        String location,
        String premise,
        Environment environment,
        List<String> resources,
        List<String> restrictions,
        List<String> rules,
        java.util.Map<String, String> phases,
        String objective,
        Discussion discussion,
        Voting voting,
        Judging judging
) {

    public record Environment(
            String climate,
            String weather,
            List<String> terrain,
            List<String> wildlife
    ) {}

    public record Discussion(
            int maxRounds,
            int maxResponseSentences
    ) {}

    public record Voting(
            boolean selfVoteAllowed,
            String tieBreaker
    ) {}

    public record Judging(
            boolean impartial,
            boolean explanationRequired
    ) {}
}