package com.steve.ai.llm.async;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.llm.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AsyncDeepSeekClient implements AsyncLLMClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncDeepSeekClient.class);
    private static final String PROVIDER_ID = "deepseek";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;

    public AsyncDeepSeekClient(String apiKey, String baseUrl, String model, int maxTokens, double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DeepSeek API key cannot be null or empty");
        }

        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        LOGGER.info("AsyncDeepSeekClient initialized (model: {}, maxTokens: {}, temperature: {})",
            model, maxTokens, temperature);
    }

    @Override
    public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        String requestBody = buildRequestBody(prompt, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DeepSeekClient.chatCompletionsUrl(baseUrl)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(60))
            .build();

        LOGGER.debug("[deepseek] Sending async request (prompt length: {} chars)", prompt.length());

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                long latencyMs = System.currentTimeMillis() - startTime;

                if (response.statusCode() != 200) {
                    LLMException.ErrorType errorType = determineErrorType(response.statusCode());
                    boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;

                    LOGGER.error("[deepseek] API error: status={}, body={}", response.statusCode(),
                        truncate(response.body(), 200));

                    throw new LLMException(
                        "DeepSeek API error: HTTP " + response.statusCode(),
                        errorType,
                        PROVIDER_ID,
                        retryable
                    );
                }

                return parseResponse(response.body(), latencyMs);
            });
    }

    private String buildRequestBody(String prompt, Map<String, Object> params) {
        JsonObject body = new JsonObject();

        String modelToUse = (String) params.getOrDefault("model", this.model);
        int maxTokensToUse = (int) params.getOrDefault("maxTokens", this.maxTokens);
        double tempToUse = (double) params.getOrDefault("temperature", this.temperature);

        body.addProperty("model", modelToUse);
        body.addProperty("max_tokens", maxTokensToUse);
        body.addProperty("temperature", tempToUse);

        JsonArray messages = new JsonArray();

        String systemPrompt = (String) params.get("systemPrompt");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JsonObject systemMessage = new JsonObject();
            systemMessage.addProperty("role", "system");
            systemMessage.addProperty("content", systemPrompt);
            messages.add(systemMessage);
        }

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        body.add("messages", messages);
        return body.toString();
    }

    private LLMResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            if (!json.has("choices") || json.getAsJsonArray("choices").isEmpty()) {
                throw new LLMException(
                    "DeepSeek response missing 'choices' array",
                    LLMException.ErrorType.INVALID_RESPONSE,
                    PROVIDER_ID,
                    false
                );
            }

            JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            int tokensUsed = 0;
            if (json.has("usage")) {
                JsonObject usage = json.getAsJsonObject("usage");
                tokensUsed = usage.get("total_tokens").getAsInt();
            }

            LOGGER.debug("[deepseek] Response received (latency: {}ms, tokens: {})", latencyMs, tokensUsed);

            return LLMResponse.builder()
                .content(content)
                .model(model)
                .providerId(PROVIDER_ID)
                .latencyMs(latencyMs)
                .tokensUsed(tokensUsed)
                .fromCache(false)
                .build();
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("[deepseek] Failed to parse response: {}", truncate(responseBody, 200), e);
            throw new LLMException(
                "Failed to parse DeepSeek response: " + e.getMessage(),
                LLMException.ErrorType.INVALID_RESPONSE,
                PROVIDER_ID,
                false,
                e
            );
        }
    }

    private LLMException.ErrorType determineErrorType(int statusCode) {
        return switch (statusCode) {
            case 429 -> LLMException.ErrorType.RATE_LIMIT;
            case 401, 403 -> LLMException.ErrorType.AUTH_ERROR;
            case 400 -> LLMException.ErrorType.CLIENT_ERROR;
            case 408, 504 -> LLMException.ErrorType.TIMEOUT;
            default -> {
                if (statusCode >= 500) {
                    yield LLMException.ErrorType.SERVER_ERROR;
                }
                yield LLMException.ErrorType.CLIENT_ERROR;
            }
        };
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return "[null]";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
