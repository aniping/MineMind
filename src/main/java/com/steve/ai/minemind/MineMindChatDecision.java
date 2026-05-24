package com.steve.ai.minemind;

public class MineMindChatDecision {
    public enum Intent {
        STATUS,
        REASON,
        NEXT_PLAN,
        GOAL_GUIDANCE,
        DIRECTION_GUIDANCE,
        PREFERENCE,
        MARK_LOCATION,
        DANGER_WARNING,
        STOP_CURRENT_ACTION,
        RESUME_AUTONOMY,
        EXPLICIT_TASK,
        CONVERSATION
    }

    private final boolean handled;
    private final String steveName;
    private final Intent intent;
    private final String content;
    private final boolean recordGuidance;

    private MineMindChatDecision(
            boolean handled,
            String steveName,
            Intent intent,
            String content,
            boolean recordGuidance) {
        this.handled = handled;
        this.steveName = steveName;
        this.intent = intent;
        this.content = content;
        this.recordGuidance = recordGuidance;
    }

    public static MineMindChatDecision ignored() {
        return new MineMindChatDecision(false, "", Intent.CONVERSATION, "", false);
    }

    public static MineMindChatDecision handled(
            String steveName,
            Intent intent,
            String content,
            boolean recordGuidance) {
        return new MineMindChatDecision(true, steveName, intent, content, recordGuidance);
    }

    public boolean isHandled() {
        return handled;
    }

    public String getSteveName() {
        return steveName;
    }

    public Intent getIntent() {
        return intent;
    }

    public String getContent() {
        return content;
    }

    public boolean shouldRecordGuidance() {
        return recordGuidance;
    }
}
