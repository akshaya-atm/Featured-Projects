package com.akshaya.outlast.model;

import java.util.List;
import java.util.Map;

/**
 * Full details of a team's challenge brainstorming phase.
 */
public record TeamChallengeResult(
        UpgradedIdea finalPlan,
        List<ChallengeIdea> pitches,
        List<UpgradedIdea> upgrades,
        Map<String, String> votes // voterId -> votedForPlanOfId
) {}
