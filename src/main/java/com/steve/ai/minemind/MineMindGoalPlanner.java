package com.steve.ai.minemind;

import com.steve.ai.config.SteveConfig;

import java.util.ArrayList;
import java.util.List;

public class MineMindGoalPlanner {

    public MineMindGoalPlan plan(MineMindObservation observation) {
        return plan(ObservationFacts.from(observation, SteveConfig.MINEMIND_COMMUNITY_MODE_ENABLED.get()));
    }

    MineMindGoalPlan plan(ObservationFacts facts) {
        List<MineMindGoal> candidates = new ArrayList<>();

        addSurvivalGoals(candidates, facts);
        addResourceGoals(candidates, facts);
        addGrowthGoals(candidates, facts);
        addCuriosityGoals(candidates, facts);
        addSocialGoals(candidates, facts);
        addCommunityGoals(candidates, facts);
        addExplorationGoals(candidates, facts);
        addMemoryGoals(candidates, facts);

        if (candidates.isEmpty()) {
            addGoal(candidates, facts, "record_observation", MineMindGoal.Type.MEMORY, 20,
                "record current situation", "fallback goal for idle observation");
        }

        return MineMindGoalPlan.fromCandidates(candidates);
    }

    private void addSurvivalGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        double healthRatio = facts.maxHealth() <= 0.0f ? 1.0 : facts.health() / facts.maxHealth();

        if (healthRatio <= 0.3 || facts.dangerLevel() == MineMindObservation.DangerLevel.CRITICAL) {
            addGoal(candidates, facts, "survive_recover", MineMindGoal.Type.SURVIVAL, 100,
                "recover and avoid danger", "health is low or danger is critical");
            return;
        }

        if (facts.dangerLevel() == MineMindObservation.DangerLevel.HIGH) {
            addGoal(candidates, facts, "find_safety", MineMindGoal.Type.SURVIVAL, 95,
                "move toward a safer area", "nearby danger is high");
        } else if (facts.dangerLevel() == MineMindObservation.DangerLevel.MEDIUM) {
            addGoal(candidates, facts, "reduce_risk", MineMindGoal.Type.SURVIVAL, 80,
                "reduce immediate risk", "nearby danger is elevated");
        }

        if (facts.night()) {
            addGoal(candidates, facts, "night_shelter", MineMindGoal.Type.SURVIVAL, 75,
                "find shelter for the night", "nighttime increases survival risk");
        }

        if (containsAny(facts.hunger(), "low", "hungry", "0/")) {
            addGoal(candidates, facts, "find_food", MineMindGoal.Type.SURVIVAL, 85,
                "find food", "hunger is low");
        }
    }

    private void addResourceGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        boolean hasBasicResources = containsAny(facts.inventory(),
            "log", "planks", "cobblestone", "stone", "dirt");

        if (hasBasicResources) {
            return;
        }

        if (isPresent(facts.nearbyResources())) {
            addGoal(candidates, facts, "gather_nearby_resources", MineMindGoal.Type.RESOURCE, 70,
                "gather nearby basic resources", "resources are visible nearby");
        } else {
            addGoal(candidates, facts, "locate_basic_resources", MineMindGoal.Type.RESOURCE, 65,
                "locate basic resources", "inventory lacks basic materials");
        }
    }

    private void addGrowthGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (!containsAny(facts.inventory(), "pickaxe", "axe", "shovel", "sword", "crafting_table")) {
            addGoal(candidates, facts, "develop_basic_tools", MineMindGoal.Type.GROWTH, 55,
                "develop basic tools", "inventory does not show basic tools");
        }
    }

    private void addCuriosityGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (isPresent(facts.nearbyResources())) {
            addGoal(candidates, facts, "inspect_resource_discovery", MineMindGoal.Type.CURIOSITY, 45,
                "inspect visible resources", "nearby resource summary changed the situation");
        }

        if (isPresent(facts.friendlyEntities())) {
            addGoal(candidates, facts, "observe_nearby_life", MineMindGoal.Type.CURIOSITY, 40,
                "observe nearby life", "friendly entities are nearby");
        }

        if (isPresent(facts.biome())) {
            addGoal(candidates, facts, "learn_current_biome", MineMindGoal.Type.CURIOSITY, 35,
                "learn current biome", "biome context can guide future choices");
        }
    }

    private void addSocialGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (isPresent(facts.nearbyPlayers())) {
            addGoal(candidates, facts, "check_player_context", MineMindGoal.Type.SOCIAL, 42,
                "check nearby player context", "players are nearby");
        } else if (isPresent(facts.friendlyEntities())) {
            addGoal(candidates, facts, "keep_social_awareness", MineMindGoal.Type.SOCIAL, 32,
                "keep social awareness", "friendly entities are nearby");
        }
    }

    private void addCommunityGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (!facts.communityModeEnabled()) {
            return;
        }

        addGoal(candidates, facts, "review_community_needs", MineMindGoal.Type.COMMUNITY, 38,
            "review community needs", "community mode is enabled");
    }

    private void addExplorationGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (facts.dangerLevel() == MineMindObservation.DangerLevel.LOW && !facts.night()) {
            addGoal(candidates, facts, "explore_unknown_area", MineMindGoal.Type.EXPLORATION, 60,
                "explore unknown area", "safe idle time favors exploration");
        } else {
            addGoal(candidates, facts, "scout_safe_route", MineMindGoal.Type.EXPLORATION, 30,
                "scout a safer route", "exploration is possible only cautiously");
        }
    }

    private void addMemoryGoals(List<MineMindGoal> candidates, ObservationFacts facts) {
        if (isPresent(facts.recentFailures())) {
            addGoal(candidates, facts, "review_recent_failures", MineMindGoal.Type.MEMORY, 50,
                "review recent failures", "recent failures should lower repeated choices");
        }

        addGoal(candidates, facts, "record_observation", MineMindGoal.Type.MEMORY, 25,
            "record current observation", "current position and discoveries may matter later");
    }

    private void addGoal(
            List<MineMindGoal> candidates,
            ObservationFacts facts,
            String id,
            MineMindGoal.Type type,
            int priority,
            String title,
            String reason) {
        MineMindGoal goal = new MineMindGoal(id, type, priority, title, reason);
        candidates.add(goal.withPriority(adjustPriority(goal, facts)));
    }

    private int adjustPriority(MineMindGoal goal, ObservationFacts facts) {
        int priority = goal.getPriority();
        String currentGoal = normalize(facts.currentGoal());
        String recentFailures = normalize(facts.recentFailures());
        String id = normalize(goal.getId());
        String title = normalize(goal.getTitle());
        String type = normalize(goal.getType().name());

        if (containsGoalSignal(currentGoal, id, title, type)) {
            priority -= 20;
        }

        if (containsGoalSignal(recentFailures, id, title, type)) {
            priority -= 25;
        }

        return Math.max(1, priority);
    }

    private static boolean containsGoalSignal(String text, String id, String title, String type) {
        return isPresent(text) && (text.contains(id) || text.contains(title) || text.contains(type));
    }

    private static boolean containsAny(String text, String... tokens) {
        String normalized = normalize(text);
        for (String token : tokens) {
            if (normalized.contains(normalize(token))) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !"none".equalsIgnoreCase(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase();
    }

    record ObservationFacts(
        float health,
        float maxHealth,
        String hunger,
        boolean night,
        String biome,
        String nearbyPlayers,
        String friendlyEntities,
        String nearbyResources,
        String inventory,
        String currentGoal,
        String recentFailures,
        MineMindObservation.DangerLevel dangerLevel,
        boolean communityModeEnabled) {

        static ObservationFacts from(MineMindObservation observation, boolean communityModeEnabled) {
            return new ObservationFacts(
                observation.getHealth(),
                observation.getMaxHealth(),
                observation.getHunger(),
                observation.isNight(),
                observation.getBiome(),
                observation.getNearbyPlayers(),
                observation.getFriendlyEntities(),
                observation.getNearbyResources(),
                observation.getInventory(),
                observation.getCurrentGoal(),
                observation.getRecentFailures(),
                observation.getDangerLevel(),
                communityModeEnabled);
        }
    }
}
