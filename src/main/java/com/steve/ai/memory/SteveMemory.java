package com.steve.ai.memory;

import com.steve.ai.entity.SteveEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class SteveMemory {
    private final SteveEntity steve;
    private String currentGoal;
    private final Queue<String> taskQueue;
    private final LinkedList<String> recentActions;
    private final LinkedList<String> recentFailures;
    private static final int MAX_RECENT_ACTIONS = 20;
    private static final int MAX_RECENT_FAILURES = 10;

    public SteveMemory(SteveEntity steve) {
        this.steve = steve;
        this.currentGoal = "";
        this.taskQueue = new LinkedList<>();
        this.recentActions = new LinkedList<>();
        this.recentFailures = new LinkedList<>();
    }

    public String getCurrentGoal() {
        return currentGoal;
    }

    public void setCurrentGoal(String goal) {
        this.currentGoal = goal;
    }

    public void addAction(String action) {
        recentActions.addLast(action);
        if (recentActions.size() > MAX_RECENT_ACTIONS) {
            recentActions.removeFirst();
        }
    }

    public void addFailure(String failure) {
        if (failure == null || failure.isBlank()) {
            return;
        }

        recentFailures.addLast(failure);
        if (recentFailures.size() > MAX_RECENT_FAILURES) {
            recentFailures.removeFirst();
        }
    }

    public List<String> getRecentActions(int count) {
        return getRecentEntries(recentActions, count);
    }

    public List<String> getRecentFailures(int count) {
        return getRecentEntries(recentFailures, count);
    }

    private List<String> getRecentEntries(LinkedList<String> entries, int count) {
        List<String> result = new ArrayList<>();
        
        int startIndex = Math.max(0, entries.size() - count);
        for (int i = startIndex; i < entries.size(); i++) {
            result.add(entries.get(i));
        }
        
        return result;
    }

    public void clearTaskQueue() {
        taskQueue.clear();
        currentGoal = "";
    }

    public void saveToNBT(CompoundTag tag) {
        tag.putString("CurrentGoal", currentGoal);
        
        ListTag actionsList = new ListTag();
        for (String action : recentActions) {
            actionsList.add(StringTag.valueOf(action));
        }
        tag.put("RecentActions", actionsList);

        ListTag failuresList = new ListTag();
        for (String failure : recentFailures) {
            failuresList.add(StringTag.valueOf(failure));
        }
        tag.put("RecentFailures", failuresList);
    }

    public void loadFromNBT(CompoundTag tag) {
        if (tag.contains("CurrentGoal")) {
            currentGoal = tag.getString("CurrentGoal");
        }
        
        if (tag.contains("RecentActions")) {
            recentActions.clear();
            ListTag actionsList = tag.getList("RecentActions", 8); // 8 = String type
            for (int i = 0; i < actionsList.size(); i++) {
                recentActions.add(actionsList.getString(i));
            }
        }

        if (tag.contains("RecentFailures")) {
            recentFailures.clear();
            ListTag failuresList = tag.getList("RecentFailures", 8); // 8 = String type
            for (int i = 0; i < failuresList.size(); i++) {
                recentFailures.add(failuresList.getString(i));
            }
        }
    }
}
