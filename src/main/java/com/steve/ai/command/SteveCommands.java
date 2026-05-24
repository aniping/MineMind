package com.steve.ai.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.entity.SteveManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class SteveCommands {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("steve")
            .then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::spawnSteve)))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::removeSteve)))
            .then(Commands.literal("list")
                .executes(SteveCommands::listSteves))
            .then(Commands.literal("stop")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(SteveCommands::stopSteve)))
            .then(Commands.literal("tell")
                .then(Commands.argument("name", StringArgumentType.string())
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(SteveCommands::tellSteve))))
            .then(Commands.literal("minemind")
                .then(Commands.literal("enable")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> setMineMindAutonomousMode(context, true))))
                .then(Commands.literal("disable")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> setMineMindAutonomousMode(context, false))))
                .then(Commands.literal("toggle")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(SteveCommands::toggleMineMindAutonomousMode)))
                .then(Commands.literal("status")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(SteveCommands::showMineMindStatus))))
        );
    }

    private static int spawnSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        ServerLevel serverLevel = source.getLevel();
        if (serverLevel == null) {
            source.sendFailure(Component.literal("Command must be run on server"));
            return 0;
        }

        SteveManager manager = SteveMod.getSteveManager();
        
        Vec3 sourcePos = source.getPosition();
        if (source.getEntity() != null) {
            Vec3 lookVec = source.getEntity().getLookAngle();
            sourcePos = sourcePos.add(lookVec.x * 3, 0, lookVec.z * 3);
        } else {
            sourcePos = sourcePos.add(3, 0, 0);
        }
        Vec3 spawnPos = sourcePos;
        
        SteveEntity steve = manager.spawnSteve(serverLevel, spawnPos, name);
        if (steve != null) {
            source.sendSuccess(() -> Component.literal("Spawned Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to spawn Steve. Name may already exist or max limit reached."));
            return 0;
        }
    }

    private static int removeSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        if (manager.removeSteve(name)) {
            source.sendSuccess(() -> Component.literal("Removed Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int listSteves(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SteveManager manager = SteveMod.getSteveManager();
        
        var names = manager.getSteveNames();
        if (names.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active Steves"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Active Steves (" + names.size() + "): " + String.join(", ", names)), false);
        }
        return 1;
    }

    private static int stopSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            steve.getActionExecutor().stopCurrentAction();
            steve.getMemory().clearTaskQueue();
            source.sendSuccess(() -> Component.literal("Stopped Steve: " + name), true);
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int tellSteve(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        String command = StringArgumentType.getString(context, "command");
        CommandSourceStack source = context.getSource();
        
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);
        
        if (steve != null) {
            // Disabled command feedback message
            // source.sendSuccess(() -> Component.literal("Instructing " + name + ": " + command), true);
            
            new Thread(() -> {
                steve.getActionExecutor().processNaturalLanguageCommand(command);
            }).start();
            
            return 1;
        } else {
            source.sendFailure(Component.literal("Steve not found: " + name));
            return 0;
        }
    }

    private static int setMineMindAutonomousMode(CommandContext<CommandSourceStack> context, boolean enabled) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        SteveEntity steve = getSteveOrFail(source, name);

        if (steve == null) {
            return 0;
        }

        steve.getMineMindState().setAutonomousModeEnabled(enabled);
        source.sendSuccess(() -> Component.literal(
            "MineMind autonomous mode " + formatEnabled(enabled) + " for Steve: " + name), true);
        return 1;
    }

    private static int toggleMineMindAutonomousMode(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        SteveEntity steve = getSteveOrFail(source, name);

        if (steve == null) {
            return 0;
        }

        boolean enabled = steve.getMineMindState().toggleAutonomousMode();
        source.sendSuccess(() -> Component.literal(
            "MineMind autonomous mode " + formatEnabled(enabled) + " for Steve: " + name), true);
        return 1;
    }

    private static int showMineMindStatus(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        CommandSourceStack source = context.getSource();
        SteveEntity steve = getSteveOrFail(source, name);

        if (steve == null) {
            return 0;
        }

        String currentGoal = steve.getActionExecutor().getCurrentGoal();
        String goalText = currentGoal == null || currentGoal.isBlank() ? "none" : currentGoal;
        String status = "MineMind status for " + name
            + ": autonomous=" + formatEnabled(steve.getMineMindState().isAutonomousModeEnabled())
            + ", chatGuidance=" + formatEnabled(SteveConfig.MINEMIND_CHAT_GUIDANCE_ENABLED.get())
            + ", community=" + formatEnabled(SteveConfig.MINEMIND_COMMUNITY_MODE_ENABLED.get())
            + ", longTermMemory=" + formatEnabled(SteveConfig.MINEMIND_LONG_TERM_MEMORY_ENABLED.get())
            + ", planner=" + (SteveConfig.MINEMIND_USE_LLM_PLANNER.get() ? "llm" : "rules")
            + ", thinkIntervalTicks=" + SteveConfig.MINEMIND_THINK_INTERVAL_TICKS.get()
            + ", maxPlanningSteps=" + SteveConfig.MINEMIND_MAX_PLANNING_STEPS.get()
            + ", currentGoal=" + goalText;

        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static SteveEntity getSteveOrFail(CommandSourceStack source, String name) {
        SteveManager manager = SteveMod.getSteveManager();
        SteveEntity steve = manager.getSteve(name);

        if (steve == null) {
            source.sendFailure(Component.literal("Steve not found: " + name));
        }

        return steve;
    }

    private static String formatEnabled(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
