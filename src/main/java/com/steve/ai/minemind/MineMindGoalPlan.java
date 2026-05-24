package com.steve.ai.minemind;

import java.util.Comparator;
import java.util.List;

public class MineMindGoalPlan {
    private static final int COMMAND_CANDIDATE_LIMIT = 8;

    private final List<MineMindGoal> candidates;

    private MineMindGoalPlan(List<MineMindGoal> candidates) {
        this.candidates = candidates.stream()
            .sorted(goalComparator())
            .toList();
    }

    public static MineMindGoalPlan fromCandidates(List<MineMindGoal> candidates) {
        return new MineMindGoalPlan(List.copyOf(candidates));
    }

    public List<MineMindGoal> getCandidates() {
        return candidates;
    }

    public MineMindGoal getSelectedGoal() {
        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(0);
    }

    public String toCommandSummary(String steveName) {
        MineMindGoal selectedGoal = getSelectedGoal();
        String selected = selectedGoal == null ? "none" : selectedGoal.toSummary();
        String candidatesText = candidates.stream()
            .limit(COMMAND_CANDIDATE_LIMIT)
            .map(MineMindGoal::toSummary)
            .reduce((left, right) -> left + " | " + right)
            .orElse("none");

        return "MineMind goals for " + steveName
            + ": selected=[" + selected + "]"
            + ", candidates=[" + candidatesText + "]";
    }

    private static Comparator<MineMindGoal> goalComparator() {
        return Comparator
            .comparingInt(MineMindGoal::getPriority)
            .reversed()
            .thenComparing((left, right) -> Integer.compare(
                right.getType().getSelectionRank(),
                left.getType().getSelectionRank()))
            .thenComparing(MineMindGoal::getId);
    }
}
