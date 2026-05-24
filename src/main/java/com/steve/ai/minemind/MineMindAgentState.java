package com.steve.ai.minemind;

import net.minecraft.nbt.CompoundTag;

public class MineMindAgentState {
    private static final String AUTONOMOUS_MODE_ENABLED_KEY = "AutonomousModeEnabled";

    private boolean autonomousModeEnabled;

    public MineMindAgentState(boolean autonomousModeEnabled) {
        this.autonomousModeEnabled = autonomousModeEnabled;
    }

    public boolean isAutonomousModeEnabled() {
        return autonomousModeEnabled;
    }

    public void setAutonomousModeEnabled(boolean autonomousModeEnabled) {
        this.autonomousModeEnabled = autonomousModeEnabled;
    }

    public boolean toggleAutonomousMode() {
        autonomousModeEnabled = !autonomousModeEnabled;
        return autonomousModeEnabled;
    }

    public void saveToNBT(CompoundTag tag) {
        tag.putBoolean(AUTONOMOUS_MODE_ENABLED_KEY, autonomousModeEnabled);
    }

    public void loadFromNBT(CompoundTag tag) {
        if (tag.contains(AUTONOMOUS_MODE_ENABLED_KEY)) {
            autonomousModeEnabled = tag.getBoolean(AUTONOMOUS_MODE_ENABLED_KEY);
        }
    }
}
