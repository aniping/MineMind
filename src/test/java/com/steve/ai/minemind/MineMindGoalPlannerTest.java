package com.steve.ai.minemind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMindGoalPlannerTest {

    @Test
    void criticalDangerPrioritizesSurvival() {
        MineMindGoalPlan plan = new MineMindGoalPlanner().plan(facts(
            4.0f,
            20.0f,
            true,
            "none",
            "none",
            "none",
            "none",
            MineMindObservation.DangerLevel.CRITICAL,
            false));

        assertEquals(MineMindGoal.Type.SURVIVAL, plan.getSelectedGoal().getType());
        assertEquals("survive_recover", plan.getSelectedGoal().getId());
    }

    @Test
    void safePreparedStateDefaultsToExploration() {
        MineMindGoalPlan plan = new MineMindGoalPlanner().plan(facts(
            20.0f,
            20.0f,
            false,
            "minecraft:plains",
            "none",
            "main=minecraft:stone_pickaxex1",
            "none",
            MineMindObservation.DangerLevel.LOW,
            false));

        assertEquals(MineMindGoal.Type.EXPLORATION, plan.getSelectedGoal().getType());
        assertEquals("explore_unknown_area", plan.getSelectedGoal().getId());
    }

    @Test
    void nearbyResourcesBeatExplorationWhenInventoryIsEmpty() {
        MineMindGoalPlan plan = new MineMindGoalPlanner().plan(facts(
            20.0f,
            20.0f,
            false,
            "minecraft:forest",
            "3 minecraft:oak_log",
            "none",
            "none",
            MineMindObservation.DangerLevel.LOW,
            false));

        assertEquals(MineMindGoal.Type.RESOURCE, plan.getSelectedGoal().getType());
        assertEquals("gather_nearby_resources", plan.getSelectedGoal().getId());
    }

    @Test
    void recentFailureDemotesMatchingGoal() {
        MineMindGoalPlan plan = new MineMindGoalPlanner().plan(facts(
            20.0f,
            20.0f,
            false,
            "minecraft:forest",
            "3 minecraft:oak_log",
            "none",
            "gather_nearby_resources failed: target blocked",
            MineMindObservation.DangerLevel.LOW,
            false));

        assertTrue(plan.getCandidates().stream()
            .filter(goal -> goal.getId().equals("gather_nearby_resources"))
            .findFirst()
            .orElseThrow()
            .getPriority() < 70);
    }

    @Test
    void communityGoalIsOnlyGeneratedWhenCommunityModeIsEnabled() {
        MineMindGoalPlan disabled = new MineMindGoalPlanner().plan(facts(
            20.0f,
            20.0f,
            false,
            "minecraft:plains",
            "none",
            "main=minecraft:stone_pickaxex1",
            "none",
            MineMindObservation.DangerLevel.LOW,
            false));
        MineMindGoalPlan enabled = new MineMindGoalPlanner().plan(facts(
            20.0f,
            20.0f,
            false,
            "minecraft:plains",
            "none",
            "main=minecraft:stone_pickaxex1",
            "none",
            MineMindObservation.DangerLevel.LOW,
            true));

        assertTrue(disabled.getCandidates().stream()
            .noneMatch(goal -> goal.getType() == MineMindGoal.Type.COMMUNITY));
        assertTrue(enabled.getCandidates().stream()
            .anyMatch(goal -> goal.getType() == MineMindGoal.Type.COMMUNITY));
    }

    private static MineMindGoalPlanner.ObservationFacts facts(
            float health,
            float maxHealth,
            boolean night,
            String biome,
            String nearbyResources,
            String inventory,
            String recentFailures,
            MineMindObservation.DangerLevel dangerLevel,
            boolean communityModeEnabled) {
        return new MineMindGoalPlanner.ObservationFacts(
            health,
            maxHealth,
            "not_applicable",
            night,
            biome,
            "none",
            "none",
            nearbyResources,
            inventory,
            "none",
            recentFailures,
            dangerLevel,
            communityModeEnabled);
    }
}
