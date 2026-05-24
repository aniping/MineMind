package com.steve.ai.minemind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMindAgentStateTest {

    @Test
    void defaultsToConfiguredValueAndToggles() {
        MineMindAgentState state = new MineMindAgentState(false);

        assertFalse(state.isAutonomousModeEnabled());
        assertTrue(state.toggleAutonomousMode());
        assertTrue(state.isAutonomousModeEnabled());
    }

    @Test
    void savesAndLoadsAutonomousModeFlag() {
        MineMindAgentState state = new MineMindAgentState(false);
        state.setAutonomousModeEnabled(true);

        CompoundTag tag = new CompoundTag();
        state.saveToNBT(tag);

        MineMindAgentState loaded = new MineMindAgentState(false);
        loaded.loadFromNBT(tag);

        assertTrue(loaded.isAutonomousModeEnabled());
    }
}
