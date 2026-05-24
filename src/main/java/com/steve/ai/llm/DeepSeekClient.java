package com.steve.ai.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.steve.ai.SteveMod;
import com.steve.ai.config.SteveConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DeepSeekClient {
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;

    private final HttpClient client;
    private final String apiKey;
    private final String baseUrl;

    public DeepSeekClient() {
        this.apiKey = SteveConfig.DEEPSEEK_API_KEY.get();
        this.baseUrl = SteveConfig.DEEPSEEK_BASE_URL.get();
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    public String sendRequest(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            SteveMod.LOGGER.error("DeepSeek API key not configured.");
            return null;
        }

        JsonObject requestBody = buildRequestBody(systemPrompt, userPrompt);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(chatCompletionsUrl(baseUrl)))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    if (responseBody == null || responseBody.isEmpty()) {
                        SteveMod.LOGGER.error("DeepSeek API returned empty response");
                        return null;
                    }
                    return parseResponse(responseBody);
                }

                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt < MAX_RETRIES - 1) {
                        int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                        SteveMod.LOGGER.warn("DeepSeek API request failed with status {}, retrying in {}ms",
                            response.statusCode(), delayMs);
                        Thread.sleep(delayMs);
                        continue;
                    }
                }

                SteveMod.LOGGER.error("DeepSeek API request failed: {}", response.statusCode());
                SteveMod.LOGGER.error("DeepSeek response body: {}", response.body());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                SteveMod.LOGGER.error("DeepSeek request interrupted", e);
                return null;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    int delayMs = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                    SteveMod.LOGGER.warn("Error communicating with DeepSeek API, retrying in {}ms", delayMs, e);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    SteveMod.LOGGER.error("Error communicating with DeepSeek API after retries", e);
                    return null;
                }
            }
        }

        return null;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", SteveConfig.DEEPSEEK_MODEL.get());
        body.addProperty("temperature", SteveConfig.DEEPSEEK_TEMPERATURE.get());
        body.addProperty("max_tokens", SteveConfig.DEEPSEEK_MAX_TOKENS.get());

        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        body.add("messages", messages);
        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

            if (json.has("choices") && !json.getAsJsonArray("choices").isEmpty()) {
                JsonObject firstChoice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
                JsonObject message = firstChoice.getAsJsonObject("message");
                if (message != null && message.has("content")) {
                    return message.get("content").getAsString();
                }
            }

            SteveMod.LOGGER.error("Unexpected DeepSeek response format: {}", responseBody);
            return null;
        } catch (Exception e) {
            SteveMod.LOGGER.error("Error parsing DeepSeek response", e);
            return null;
        }
    }

    public static String chatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl == null || baseUrl.isBlank()
            ? "https://api.deepseek.com"
            : baseUrl.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }

        return normalized + "/chat/completions";
    }
}
