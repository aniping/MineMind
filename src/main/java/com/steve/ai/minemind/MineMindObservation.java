package com.steve.ai.minemind;

import com.steve.ai.entity.SteveEntity;
import com.steve.ai.memory.WorldKnowledge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MineMindObservation {
    private static final int SUMMARY_LIMIT = 5;

    public enum DangerLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private final String steveName;
    private final String position;
    private final float health;
    private final float maxHealth;
    private final String hunger;
    private final String dimension;
    private final long dayTime;
    private final boolean night;
    private final String biome;
    private final String nearbyPlayers;
    private final String friendlyEntities;
    private final String hostileEntities;
    private final String nearbyBlocks;
    private final String nearbyResources;
    private final String inventory;
    private final String currentAction;
    private final int queuedTaskCount;
    private final String currentGoal;
    private final String recentFailures;
    private final String recentPlayerGuidance;
    private final DangerLevel dangerLevel;
    private final List<String> dangerReasons;

    private MineMindObservation(
            String steveName,
            String position,
            float health,
            float maxHealth,
            String hunger,
            String dimension,
            long dayTime,
            boolean night,
            String biome,
            String nearbyPlayers,
            String friendlyEntities,
            String hostileEntities,
            String nearbyBlocks,
            String nearbyResources,
            String inventory,
            String currentAction,
            int queuedTaskCount,
            String currentGoal,
            String recentFailures,
            String recentPlayerGuidance,
            DangerLevel dangerLevel,
            List<String> dangerReasons) {
        this.steveName = steveName;
        this.position = position;
        this.health = health;
        this.maxHealth = maxHealth;
        this.hunger = hunger;
        this.dimension = dimension;
        this.dayTime = dayTime;
        this.night = night;
        this.biome = biome;
        this.nearbyPlayers = nearbyPlayers;
        this.friendlyEntities = friendlyEntities;
        this.hostileEntities = hostileEntities;
        this.nearbyBlocks = nearbyBlocks;
        this.nearbyResources = nearbyResources;
        this.inventory = inventory;
        this.currentAction = currentAction;
        this.queuedTaskCount = queuedTaskCount;
        this.currentGoal = currentGoal;
        this.recentFailures = recentFailures;
        this.recentPlayerGuidance = recentPlayerGuidance;
        this.dangerLevel = dangerLevel;
        this.dangerReasons = List.copyOf(dangerReasons);
    }

    public static MineMindObservation capture(SteveEntity steve) {
        WorldKnowledge worldKnowledge = new WorldKnowledge(steve);
        Level level = steve.level();
        BlockPos pos = steve.blockPosition();
        long dayTime = level.getDayTime();
        boolean night = isNight(dayTime);
        List<Entity> nearbyEntities = worldKnowledge.getNearbyEntities();
        Map<Block, Integer> nearbyBlocks = worldKnowledge.getNearbyBlocks();
        DangerAssessment danger = assessDanger(steve, nearbyEntities, night);

        String currentGoal = steve.getMemory().getCurrentGoal();
        if (currentGoal == null || currentGoal.isBlank()) {
            currentGoal = "none";
        }

        return new MineMindObservation(
            steve.getSteveName(),
            formatPosition(pos),
            steve.getHealth(),
            steve.getMaxHealth(),
            "not_applicable",
            level.dimension().location().toString(),
            dayTime,
            night,
            worldKnowledge.getBiomeName(),
            worldKnowledge.getNearbyPlayerNames(),
            summarizeEntities(nearbyEntities, EntityGroup.FRIENDLY),
            summarizeEntities(nearbyEntities, EntityGroup.HOSTILE),
            summarizeBlocks(nearbyBlocks, false),
            summarizeBlocks(nearbyBlocks, true),
            summarizeInventory(steve),
            steve.getActionExecutor().getCurrentActionDescription(),
            steve.getActionExecutor().getQueuedTaskCount(),
            currentGoal,
            summarizeList(steve.getMemory().getRecentFailures(3)),
            summarizeList(steve.getMemory().getRecentPlayerGuidance(3)),
            danger.level(),
            danger.reasons()
        );
    }

    static MineMindObservation syntheticForTesting(
            String steveName,
            String position,
            float health,
            float maxHealth,
            String hunger,
            String dimension,
            long dayTime,
            boolean night,
            String biome,
            String nearbyPlayers,
            String friendlyEntities,
            String hostileEntities,
            String nearbyBlocks,
            String nearbyResources,
            String inventory,
            String currentAction,
            int queuedTaskCount,
            String currentGoal,
            String recentFailures,
            String recentPlayerGuidance,
            DangerLevel dangerLevel,
            List<String> dangerReasons) {
        return new MineMindObservation(
            steveName,
            position,
            health,
            maxHealth,
            hunger,
            dimension,
            dayTime,
            night,
            biome,
            nearbyPlayers,
            friendlyEntities,
            hostileEntities,
            nearbyBlocks,
            nearbyResources,
            inventory,
            currentAction,
            queuedTaskCount,
            currentGoal,
            recentFailures,
            recentPlayerGuidance,
            dangerLevel,
            dangerReasons);
    }

    public String toCommandSummary() {
        return "Observation for " + steveName
            + ": pos=" + position
            + ", health=" + formatHealth()
            + ", hunger=" + hunger
            + ", dimension=" + dimension
            + ", time=" + dayTime + (night ? " night" : " day")
            + ", biome=" + biome
            + ", danger=" + dangerLevel + " (" + summarizeList(dangerReasons) + ")"
            + ", players=" + nearbyPlayers
            + ", friendly=" + friendlyEntities
            + ", hostile=" + hostileEntities
            + ", resources=" + nearbyResources
            + ", inventory=" + inventory
            + ", action=" + currentAction
            + ", queuedTasks=" + queuedTaskCount
            + ", goal=" + currentGoal
            + ", failures=" + recentFailures
            + ", guidance=" + recentPlayerGuidance;
    }

    public String toMemorySummary() {
        return "pos=" + position
            + ", health=" + formatHealth()
            + ", time=" + (night ? "night" : "day")
            + ", biome=" + biome
            + ", danger=" + dangerLevel
            + ", resources=" + nearbyResources
            + ", goal=" + currentGoal
            + ", guidance=" + recentPlayerGuidance;
    }

    public DangerLevel getDangerLevel() {
        return dangerLevel;
    }

    public String getSteveName() {
        return steveName;
    }

    public String getPosition() {
        return position;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public String getHunger() {
        return hunger;
    }

    public String getDimension() {
        return dimension;
    }

    public long getDayTime() {
        return dayTime;
    }

    public boolean isNight() {
        return night;
    }

    public String getBiome() {
        return biome;
    }

    public String getNearbyPlayers() {
        return nearbyPlayers;
    }

    public String getFriendlyEntities() {
        return friendlyEntities;
    }

    public String getHostileEntities() {
        return hostileEntities;
    }

    public List<String> getDangerReasons() {
        return dangerReasons;
    }

    public String getNearbyBlocks() {
        return nearbyBlocks;
    }

    public String getNearbyResources() {
        return nearbyResources;
    }

    public String getInventory() {
        return inventory;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public int getQueuedTaskCount() {
        return queuedTaskCount;
    }

    public String getCurrentGoal() {
        return currentGoal;
    }

    public String getRecentFailures() {
        return recentFailures;
    }

    public String getRecentPlayerGuidance() {
        return recentPlayerGuidance;
    }

    private String formatHealth() {
        return String.format("%.1f/%.1f", health, maxHealth);
    }

    private static DangerAssessment assessDanger(SteveEntity steve, List<Entity> nearbyEntities, boolean night) {
        List<String> reasons = new ArrayList<>();
        DangerLevel level = DangerLevel.LOW;
        long hostileCount = nearbyEntities.stream().filter(entity -> entity instanceof Monster).count();

        if (steve.getHealth() <= 6.0f) {
            level = max(level, DangerLevel.CRITICAL);
            reasons.add("low_health");
        } else if (steve.getHealth() <= 10.0f) {
            level = max(level, DangerLevel.MEDIUM);
            reasons.add("reduced_health");
        }

        if (hostileCount >= 3) {
            level = max(level, DangerLevel.HIGH);
            reasons.add("many_hostiles");
        } else if (hostileCount > 0) {
            level = max(level, DangerLevel.MEDIUM);
            reasons.add("nearby_hostiles");
        }

        if (night && hostileCount > 0) {
            level = max(level, DangerLevel.HIGH);
            reasons.add("night_hostiles");
        } else if (night) {
            level = max(level, DangerLevel.MEDIUM);
            reasons.add("night");
        }

        if (steve.isOnFire()) {
            level = max(level, DangerLevel.HIGH);
            reasons.add("on_fire");
        }

        if (steve.getAirSupply() < steve.getMaxAirSupply() / 2) {
            level = max(level, DangerLevel.MEDIUM);
            reasons.add("low_air");
        }

        if (reasons.isEmpty()) {
            reasons.add("stable");
        }

        return new DangerAssessment(level, reasons);
    }

    private static DangerLevel max(DangerLevel current, DangerLevel candidate) {
        return candidate.ordinal() > current.ordinal() ? candidate : current;
    }

    private static String summarizeEntities(List<Entity> entities, EntityGroup group) {
        Map<String, Integer> counts = new HashMap<>();

        for (Entity entity : entities) {
            if (group.matches(entity)) {
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                counts.put(name, counts.getOrDefault(name, 0) + 1);
            }
        }

        return summarizeCounts(counts);
    }

    private static String summarizeBlocks(Map<Block, Integer> blocks, boolean resourcesOnly) {
        Map<String, Integer> counts = new HashMap<>();

        for (Map.Entry<Block, Integer> entry : blocks.entrySet()) {
            String name = BuiltInRegistries.BLOCK.getKey(entry.getKey()).toString();
            if (!resourcesOnly || isResourceBlock(name)) {
                counts.put(name, counts.getOrDefault(name, 0) + entry.getValue());
            }
        }

        return summarizeCounts(counts);
    }

    private static boolean isResourceBlock(String blockName) {
        return blockName.contains("_ore")
            || blockName.endsWith("_log")
            || blockName.endsWith("_stem")
            || blockName.endsWith("_leaves")
            || blockName.contains("coal")
            || blockName.contains("iron")
            || blockName.contains("copper")
            || blockName.contains("gold")
            || blockName.contains("diamond")
            || blockName.contains("redstone")
            || blockName.contains("lapis")
            || blockName.contains("emerald");
    }

    private static String summarizeInventory(SteveEntity steve) {
        ItemStack mainHand = steve.getMainHandItem();
        ItemStack offHand = steve.getOffhandItem();
        List<String> items = new ArrayList<>();

        if (!mainHand.isEmpty()) {
            items.add("main=" + formatItem(mainHand));
        }

        if (!offHand.isEmpty()) {
            items.add("off=" + formatItem(offHand));
        }

        return summarizeList(items);
    }

    private static String formatItem(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    private static String summarizeCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }

        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(SUMMARY_LIMIT)
            .map(entry -> entry.getValue() + " " + entry.getKey())
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private static String summarizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }

        return String.join(", ", values);
    }

    private static String formatPosition(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static boolean isNight(long dayTime) {
        long timeOfDay = dayTime % 24000L;
        return timeOfDay >= 13000L && timeOfDay <= 23000L;
    }

    private enum EntityGroup {
        FRIENDLY {
            @Override
            boolean matches(Entity entity) {
                return entity instanceof Player || entity instanceof SteveEntity || entity instanceof Animal;
            }
        },
        HOSTILE {
            @Override
            boolean matches(Entity entity) {
                return entity instanceof Monster;
            }
        };

        abstract boolean matches(Entity entity);
    }

    private record DangerAssessment(DangerLevel level, List<String> reasons) {
    }
}
