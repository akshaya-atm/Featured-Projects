package com.akshaya.outlast.model;

import java.util.List;

public record Challenge(
        ChallengeTheme theme,
        String title,
        String scenario,
        String objective,
        List<String> resources,
        List<String> constraints,
        List<String> successCriteria
) {
}