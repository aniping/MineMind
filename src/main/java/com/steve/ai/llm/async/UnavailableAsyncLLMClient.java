package com.steve.ai.llm.async;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UnavailableAsyncLLMClient implements AsyncLLMClient {
    private final String providerId;
    private final String reason;

    public UnavailableAsyncLLMClient(String providerId, String reason) {
        this.providerId = providerId;
        this.reason = reason;
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        return CompletableFuture.failedFuture(new LLMException(
            reason,
            LLMException.ErrorType.CLIENT_ERROR,
            providerId,
            false
        ));
    }

    @Override
    public String getProviderId() {
        return providerId;
    }

    @Override
    public boolean isHealthy() {
        return false;
    }
}
