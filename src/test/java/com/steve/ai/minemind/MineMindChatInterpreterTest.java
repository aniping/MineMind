package com.steve.ai.minemind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MineMindChatInterpreterTest {

    @Test
    void ignoresMessagesWithoutSteveName() {
        MineMindChatDecision decision = new MineMindChatInterpreter().interpret(
            "please find a village",
            List.of("Bob"));

        assertFalse(decision.isHandled());
    }

    @Test
    void classifiesStatusQuestionForMentionedSteve() {
        MineMindChatDecision decision = new MineMindChatInterpreter().interpret(
            "Bob, what are you doing?",
            List.of("Bob", "Alice"));

        assertTrue(decision.isHandled());
        assertEquals("Bob", decision.getSteveName());
        assertEquals(MineMindChatDecision.Intent.STATUS, decision.getIntent());
        assertFalse(decision.shouldRecordGuidance());
    }

    @Test
    void recordsDirectionGuidance() {
        MineMindChatDecision decision = new MineMindChatInterpreter().interpret(
            "Bob, don't go east",
            List.of("Bob"));

        assertEquals(MineMindChatDecision.Intent.DIRECTION_GUIDANCE, decision.getIntent());
        assertTrue(decision.shouldRecordGuidance());
    }

    @Test
    void classifiesExplicitTask() {
        MineMindChatDecision decision = new MineMindChatInterpreter().interpret(
            "Bob gather wood",
            List.of("Bob"));

        assertEquals(MineMindChatDecision.Intent.EXPLICIT_TASK, decision.getIntent());
        assertEquals("gather wood", decision.getContent());
    }
}
