package com.steve.ai.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeepSeekClientTest {

    @Test
    void defaultsBlankBaseUrlToDeepSeekChatEndpoint() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekClient.chatCompletionsUrl(" ")
        );
    }

    @Test
    void appendsChatEndpointToBaseUrl() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekClient.chatCompletionsUrl("https://api.deepseek.com")
        );
    }

    @Test
    void trimsTrailingSlashBeforeAppendingEndpoint() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekClient.chatCompletionsUrl("https://api.deepseek.com/")
        );
    }

    @Test
    void keepsFullChatEndpointUnchanged() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            DeepSeekClient.chatCompletionsUrl("https://api.deepseek.com/chat/completions")
        );
    }
}
