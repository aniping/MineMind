package com.steve.ai.minemind;

public class MineMindGoal {
    public enum Type {
        SURVIVAL(80),
        RESOURCE(70),
        GROWTH(60),
        COMMUNITY(50),
        SOCIAL(40),
        CURIOSITY(30),
        EXPLORATION(20),
        MEMORY(10);

        private final int selectionRank;

        Type(int selectionRank) {
            this.selectionRank = selectionRank;
        }

        public int getSelectionRank() {
            return selectionRank;
        }
    }

    private final String id;
    private final Type type;
    private final int priority;
    private final String title;
    private final String reason;

    public MineMindGoal(String id, Type type, int priority, String title, String reason) {
        this.id = id;
        this.type = type;
        this.priority = priority;
        this.title = title;
        this.reason = reason;
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    public String getTitle() {
        return title;
    }

    public String getReason() {
        return reason;
    }

    MineMindGoal withPriority(int adjustedPriority) {
        return new MineMindGoal(id, type, adjustedPriority, title, reason);
    }

    public String toSummary() {
        return type + ":" + title + " p=" + priority + " reason=" + reason;
    }
}
