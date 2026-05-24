package com.steve.ai.memory;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SteveMemoryTest {

    @Test
    void recordsRecentFailures() {
        SteveMemory memory = new SteveMemory(null);

        memory.addFailure("path blocked");
        memory.addFailure("target missing");

        assertEquals(List.of("path blocked", "target missing"), memory.getRecentFailures(5));
        assertEquals(List.of("target missing"), memory.getRecentFailures(1));
    }

    @Test
    void savesAndLoadsRecentFailures() {
        SteveMemory memory = new SteveMemory(null);
        memory.addFailure("build failed");

        CompoundTag tag = new CompoundTag();
        memory.saveToNBT(tag);

        SteveMemory loaded = new SteveMemory(null);
        loaded.loadFromNBT(tag);

        assertEquals(List.of("build failed"), loaded.getRecentFailures(3));
    }

    @Test
    void savesAndLoadsRecentObservations() {
        SteveMemory memory = new SteveMemory(null);
        memory.addObservation("pos=0,64,0, danger=LOW");

        CompoundTag tag = new CompoundTag();
        memory.saveToNBT(tag);

        SteveMemory loaded = new SteveMemory(null);
        loaded.loadFromNBT(tag);

        assertEquals(List.of("pos=0,64,0, danger=LOW"), loaded.getRecentObservations(3));
    }
}
