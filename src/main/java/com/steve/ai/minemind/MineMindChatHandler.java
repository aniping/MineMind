package com.steve.ai.minemind;

import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MineMindChatHandler {
    private static final MineMindChatInterpreter INTERPRETER = new MineMindChatInterpreter();
    private static final Map<UUID, Long> LAST_REPLY_TICK_BY_STEVE = new HashMap<>();

    private MineMindChatHandler() {
    }

    public static boolean handle(ServerPlayer player, String rawText) {
        if (!SteveConfig.MINEMIND_CHAT_GUIDANCE_ENABLED.get()) {
            return false;
        }

        SteveManager manager = SteveMod.getSteveManager();
        MineMindChatDecision decision = INTERPRETER.interpret(rawText, manager.getSteveNames());
        if (!decision.isHandled()) {
            return false;
        }

        SteveEntity steve = manager.getSteve(decision.getSteveName());
        if (steve == null) {
            return false;
        }

        if (decision.shouldRecordGuidance()) {
            steve.getMemory().addPlayerGuidance(player.getName().getString() + ": " + decision.getContent());
        }

        applyDecision(steve, decision);
        return true;
    }

    private static void applyDecision(SteveEntity steve, MineMindChatDecision decision) {
        switch (decision.getIntent()) {
            case STATUS -> replyIfAllowed(steve, statusReply(steve));
            case REASON -> replyIfAllowed(steve, reasonReply(steve));
            case NEXT_PLAN -> replyIfAllowed(steve, nextPlanReply(steve));
            case STOP_CURRENT_ACTION -> {
                steve.getActionExecutor().stopCurrentAction();
                replyIfAllowed(steve, "Stopped my current action.");
            }
            case RESUME_AUTONOMY -> {
                steve.getMineMindState().setAutonomousModeEnabled(true);
                steve.getMemory().addPlayerGuidance("resume autonomy: " + decision.getContent());
                replyIfAllowed(steve, "Autonomous mode is enabled again.");
            }
            case EXPLICIT_TASK -> {
                steve.getActionExecutor().processNaturalLanguageCommand(decision.getContent());
                replyIfAllowed(steve, "I will treat that as a task.");
            }
            case GOAL_GUIDANCE, DIRECTION_GUIDANCE, PREFERENCE, MARK_LOCATION, DANGER_WARNING ->
                replyIfAllowed(steve, "Noted. I will remember that guidance.");
            case CONVERSATION -> replyIfAllowed(steve, "I heard you.");
        }
    }

    private static String statusReply(SteveEntity steve) {
        return "Goal: " + noneIfBlank(steve.getMemory().getCurrentGoal())
            + "; action: " + steve.getActionExecutor().getCurrentActionDescription()
            + "; autonomous: " + (steve.getMineMindState().isAutonomousModeEnabled() ? "on" : "off");
    }

    private static String reasonReply(SteveEntity steve) {
        String goal = noneIfBlank(steve.getMemory().getCurrentGoal());
        if ("none".equals(goal)) {
            return "I do not have a current goal yet.";
        }

        return "I am following the current goal: " + goal;
    }

    private static String nextPlanReply(SteveEntity steve) {
        MineMindObservation observation = MineMindObservation.capture(steve);
        MineMindGoalPlan plan = new MineMindGoalPlanner().plan(observation);
        MineMindGoal selected = plan.getSelectedGoal();
        if (selected == null) {
            return "I do not have a next MineMind goal yet.";
        }

        return "Next likely goal: " + selected.getTitle() + ".";
    }

    private static void replyIfAllowed(SteveEntity steve, String message) {
        long now = steve.level().getGameTime();
        Long lastReply = LAST_REPLY_TICK_BY_STEVE.get(steve.getUUID());
        if (lastReply != null && now - lastReply < SteveConfig.MINEMIND_CHAT_RESPONSE_COOLDOWN_TICKS.get()) {
            return;
        }

        LAST_REPLY_TICK_BY_STEVE.put(steve.getUUID(), now);
        steve.sendChatMessage(message);
    }

    private static String noneIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }

        return value;
    }
}
