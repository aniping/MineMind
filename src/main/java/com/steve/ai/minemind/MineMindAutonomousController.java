package com.steve.ai.minemind;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;

import java.util.List;

public class MineMindAutonomousController {
    private final SteveEntity steve;
    private final MineMindGoalPlanner goalPlanner;
    private final MineMindRuleTaskPlanner taskPlanner;
    private int ticksSinceLastThink;

    public MineMindAutonomousController(SteveEntity steve) {
        this.steve = steve;
        this.goalPlanner = new MineMindGoalPlanner();
        this.taskPlanner = new MineMindRuleTaskPlanner();
        this.ticksSinceLastThink = 0;
    }

    public void tick() {
        if (!steve.getMineMindState().isAutonomousModeEnabled()) {
            ticksSinceLastThink = 0;
            return;
        }

        if (!steve.getActionExecutor().canAcceptAutonomousTasks()) {
            return;
        }

        ticksSinceLastThink++;
        int interval = SteveConfig.MINEMIND_THINK_INTERVAL_TICKS.get();
        if (ticksSinceLastThink < interval) {
            return;
        }

        ticksSinceLastThink = 0;
        runDecisionCycle();
    }

    private void runDecisionCycle() {
        MineMindObservation observation = MineMindObservation.capture(steve);
        steve.getMemory().addObservation(observation.toMemorySummary());

        MineMindGoalPlan goalPlan = goalPlanner.plan(observation);
        MineMindGoal selectedGoal = goalPlan.getSelectedGoal();
        if (selectedGoal == null) {
            SteveMod.LOGGER.debug("Steve '{}' MineMind cycle found no candidate goal", steve.getSteveName());
            return;
        }

        List<Task> tasks = taskPlanner.planTasks(selectedGoal, steve.blockPosition(), observation);
        int maxSteps = SteveConfig.MINEMIND_MAX_PLANNING_STEPS.get();
        if (tasks.size() > maxSteps) {
            tasks = tasks.subList(0, maxSteps);
        }

        boolean submitted = steve.getActionExecutor().submitAutonomousTasks(
            "MineMind " + selectedGoal.getType() + ": " + selectedGoal.getTitle(),
            tasks);

        if (submitted) {
            SteveMod.LOGGER.info("Steve '{}' MineMind selected goal: {}",
                steve.getSteveName(), selectedGoal.toSummary());
        }
    }
}
