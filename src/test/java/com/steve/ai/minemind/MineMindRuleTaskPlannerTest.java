package com.steve.ai.minemind;

import com.steve.ai.action.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MineMindRuleTaskPlannerTest {

    @Test
    void survivalGoalPlansPathfindAwayFromCurrentPosition() {
        MineMindGoal goal = new MineMindGoal(
            "find_safety",
            MineMindGoal.Type.SURVIVAL,
            95,
            "move toward a safer area",
            "nearby danger is high");

        List<Task> tasks = new MineMindRuleTaskPlanner().planTasks(
            goal,
            10,
            64,
            20,
            observation("none"));

        assertEquals("pathfind", tasks.get(0).getAction());
        assertEquals(18, tasks.get(0).getIntParameter("x", 0));
        assertEquals(64, tasks.get(0).getIntParameter("y", 0));
        assertEquals(28, tasks.get(0).getIntParameter("z", 0));
    }

    @Test
    void resourceGoalPlansMineTaskForVisibleLogs() {
        MineMindGoal goal = new MineMindGoal(
            "gather_nearby_resources",
            MineMindGoal.Type.RESOURCE,
            70,
            "gather nearby basic resources",
            "resources are visible nearby");

        List<Task> tasks = new MineMindRuleTaskPlanner().planTasks(
            goal,
            0,
            64,
            0,
            observation("3 minecraft:oak_log"));

        assertEquals("mine", tasks.get(0).getAction());
        assertEquals("oak_log", tasks.get(0).getStringParameter("block"));
        assertEquals(3, tasks.get(0).getIntParameter("quantity", 0));
    }

    @Test
    void unsupportedGoalFallsBackToWait() {
        MineMindGoal goal = new MineMindGoal(
            "record_observation",
            MineMindGoal.Type.MEMORY,
            25,
            "record current observation",
            "current position and discoveries may matter later");

        List<Task> tasks = new MineMindRuleTaskPlanner().planTasks(
            goal,
            0,
            64,
            0,
            observation("none"));

        assertEquals("wait", tasks.get(0).getAction());
        assertEquals(60, tasks.get(0).getIntParameter("ticks", 0));
    }

    private static MineMindObservation observation(String nearbyResources) {
        return MineMindObservation.syntheticForTesting(
            "Bob",
            "0,64,0",
            20.0f,
            20.0f,
            "not_applicable",
            "minecraft:overworld",
            1000L,
            false,
            "minecraft:plains",
            "none",
            "none",
            "none",
            "none",
            nearbyResources,
            "none",
            "none",
            0,
            "none",
            "none",
            "none",
            MineMindObservation.DangerLevel.LOW,
            List.of("stable"));
    }
}
