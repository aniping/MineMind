package com.steve.ai.minemind;

import com.steve.ai.action.Task;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;

public class MineMindRuleTaskPlanner {
    private static final int SHORT_WAIT_TICKS = 60;

    public List<Task> planTasks(MineMindGoal goal, BlockPos origin, MineMindObservation observation) {
        return planTasks(goal, origin.getX(), origin.getY(), origin.getZ(), observation);
    }

    List<Task> planTasks(MineMindGoal goal, int originX, int originY, int originZ, MineMindObservation observation) {
        if (goal == null) {
            return waitTasks(SHORT_WAIT_TICKS);
        }

        return switch (goal.getId()) {
            case "survive_recover", "find_safety", "reduce_risk", "night_shelter", "scout_safe_route" ->
                List.of(pathfindTask(originX + 8, originY, originZ + 8));
            case "find_food" -> waitTasks(SHORT_WAIT_TICKS);
            case "gather_nearby_resources" ->
                List.of(mineTask(selectResourceBlock(observation.getNearbyResources()), 3));
            case "locate_basic_resources", "explore_unknown_area" ->
                List.of(pathfindTask(originX + 12, originY, originZ + 4));
            case "develop_basic_tools" -> List.of(mineTask("stone", 4));
            case "check_player_context" -> List.of(followTask("me"));
            case "follow_player_guidance" -> waitTasks(SHORT_WAIT_TICKS);
            default -> waitTasks(SHORT_WAIT_TICKS);
        };
    }

    private static Task pathfindTask(int x, int y, int z) {
        return new Task("pathfind", Map.of(
            "x", x,
            "y", y,
            "z", z));
    }

    private static Task mineTask(String block, int quantity) {
        return new Task("mine", Map.of(
            "block", block,
            "quantity", quantity));
    }

    private static Task followTask(String player) {
        return new Task("follow", Map.of("player", player));
    }

    private static List<Task> waitTasks(int ticks) {
        return List.of(new Task("wait", Map.of("ticks", Math.max(1, ticks))));
    }

    private static String selectResourceBlock(String nearbyResources) {
        String resources = nearbyResources == null ? "" : nearbyResources.toLowerCase();

        if (resources.contains("_log") || resources.contains("log")) {
            return "oak_log";
        }
        if (resources.contains("coal")) {
            return "coal";
        }
        if (resources.contains("iron")) {
            return "iron";
        }
        if (resources.contains("copper")) {
            return "copper";
        }

        return "stone";
    }
}
