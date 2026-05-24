package com.steve.ai.minemind;

import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Pattern;

public class MineMindChatInterpreter {

    public MineMindChatDecision interpret(String rawText, Collection<String> steveNames) {
        if (rawText == null || rawText.isBlank() || steveNames == null || steveNames.isEmpty()) {
            return MineMindChatDecision.ignored();
        }

        String steveName = findMentionedSteve(rawText, steveNames);
        if (steveName == null) {
            return MineMindChatDecision.ignored();
        }

        String content = stripSteveName(rawText, steveName);
        MineMindChatDecision.Intent intent = classify(content);
        return MineMindChatDecision.handled(
            steveName,
            intent,
            content,
            shouldRecordGuidance(intent));
    }

    private static String findMentionedSteve(String rawText, Collection<String> steveNames) {
        String normalized = normalize(rawText);
        return steveNames.stream()
            .filter(name -> normalized.contains(normalize(name)))
            .max(Comparator.comparingInt(String::length))
            .orElse(null);
    }

    private static String stripSteveName(String rawText, String steveName) {
        String content = rawText.replaceFirst("(?i)" + Pattern.quote(steveName), "");
        content = content.replaceAll("^[\\s,，:：;；]+", "");
        return content.trim();
    }

    private static MineMindChatDecision.Intent classify(String content) {
        String text = normalize(content);

        if (containsAny(text, "stop", "cancel", "停止", "停下", "别动", "取消")) {
            return MineMindChatDecision.Intent.STOP_CURRENT_ACTION;
        }
        if (containsAny(text, "resume", "continue autonomous", "继续自主", "继续探索", "恢复自主")) {
            return MineMindChatDecision.Intent.RESUME_AUTONOMY;
        }
        if (containsAny(text, "why", "reason", "为什么", "为啥")) {
            return MineMindChatDecision.Intent.REASON;
        }
        if (containsAny(text, "next", "plan", "接下来", "下一步", "准备")) {
            return MineMindChatDecision.Intent.NEXT_PLAN;
        }
        if (containsAny(text, "status", "doing", "what are you doing", "你在干什么", "你在做什么", "状态")) {
            return MineMindChatDecision.Intent.STATUS;
        }
        if (containsAny(text, "remember here", "mark", "base", "记住这里", "标记", "基地", "资源点", "危险点")) {
            return MineMindChatDecision.Intent.MARK_LOCATION;
        }
        if (containsAny(text, "danger", "warning", "careful", "危险", "小心", "警告")) {
            return MineMindChatDecision.Intent.DANGER_WARNING;
        }
        if (containsAny(text, "avoid", "do not go", "don't go", "别去", "不要去", "避开")) {
            return MineMindChatDecision.Intent.DIRECTION_GUIDANCE;
        }
        if (containsAny(text, "prefer", "priority", "first", "优先", "偏好", "尽量")) {
            return MineMindChatDecision.Intent.GOAL_GUIDANCE;
        }
        if (containsAny(text, "please", "try", "建议", "希望", "不要攻击", "夜晚不要")) {
            return MineMindChatDecision.Intent.PREFERENCE;
        }
        if (containsAny(text, "mine", "build", "follow", "attack", "gather", "collect", "挖", "建", "跟随", "攻击", "采集")) {
            return MineMindChatDecision.Intent.EXPLICIT_TASK;
        }

        return MineMindChatDecision.Intent.CONVERSATION;
    }

    private static boolean shouldRecordGuidance(MineMindChatDecision.Intent intent) {
        return switch (intent) {
            case GOAL_GUIDANCE, DIRECTION_GUIDANCE, PREFERENCE, MARK_LOCATION, DANGER_WARNING -> true;
            default -> false;
        };
    }

    private static boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(normalize(token))) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase();
    }
}
