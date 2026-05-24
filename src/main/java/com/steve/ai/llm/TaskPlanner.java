package com.steve.ai.llm;

import com.steve.ai.SteveMod;
import com.steve.ai.action.Task;
import com.steve.ai.config.SteveConfig;
import com.steve.ai.entity.SteveEntity;
import com.steve.ai.llm.async.*;
import com.steve.ai.llm.resilience.LLMFallbackHandler;
import com.steve.ai.llm.resilience.ResilientLLMClient;
import com.steve.ai.memory.WorldKnowledge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TaskPlanner {
    // Legacy synchronous clients (for backward compatibility)
    private final OpenAIClient openAIClient;
    private final GeminiClient geminiClient;
    private final GroqClient groqClient;
    private final DeepSeekClient deepSeekClient;

    // NEW: Async resilient clients
    private final AsyncLLMClient asyncOpenAIClient;
    private final AsyncLLMClient asyncGroqClient;
    private final AsyncLLMClient asyncGeminiClient;
    private final AsyncLLMClient asyncDeepSeekClient;
    private final LLMCache llmCache;
    private final LLMFallbackHandler fallbackHandler;

    public TaskPlanner() {
        // Legacy clients
        this.openAIClient = new OpenAIClient();
        this.geminiClient = new GeminiClient();
        this.groqClient = new GroqClient();
        this.deepSeekClient = new DeepSeekClient();

        // Initialize async infrastructure
        this.llmCache = new LLMCache();
        this.fallbackHandler = new LLMFallbackHandler();

        // Initialize async clients with resilience wrappers
        this.asyncDeepSeekClient = resilientOrUnavailable("deepseek", () -> new AsyncDeepSeekClient(
            SteveConfig.DEEPSEEK_API_KEY.get(),
            SteveConfig.DEEPSEEK_BASE_URL.get(),
            SteveConfig.DEEPSEEK_MODEL.get(),
            SteveConfig.DEEPSEEK_MAX_TOKENS.get(),
            SteveConfig.DEEPSEEK_TEMPERATURE.get()));
        this.asyncOpenAIClient = resilientOrUnavailable("openai", () -> new AsyncOpenAIClient(
            SteveConfig.OPENAI_API_KEY.get(),
            SteveConfig.OPENAI_MODEL.get(),
            SteveConfig.MAX_TOKENS.get(),
            SteveConfig.TEMPERATURE.get()));
        this.asyncGroqClient = resilientOrUnavailable("groq", () -> new AsyncGroqClient(
            SteveConfig.OPENAI_API_KEY.get(),
            "llama-3.1-8b-instant",
            500,
            SteveConfig.TEMPERATURE.get()));
        this.asyncGeminiClient = resilientOrUnavailable("gemini", () -> new AsyncGeminiClient(
            SteveConfig.OPENAI_API_KEY.get(),
            "gemini-1.5-flash",
            SteveConfig.MAX_TOKENS.get(),
            SteveConfig.TEMPERATURE.get()));

        SteveMod.LOGGER.info("TaskPlanner initialized with async resilient clients");
    }

    private AsyncLLMClient resilientOrUnavailable(String provider, AsyncClientFactory factory) {
        try {
            return new ResilientLLMClient(factory.create(), llmCache, fallbackHandler);
        } catch (IllegalArgumentException e) {
            SteveMod.LOGGER.warn("Async {} client unavailable: {}", provider, e.getMessage());
            return new UnavailableAsyncLLMClient(provider, e.getMessage());
        }
    }

    public ResponseParser.ParsedResponse planTasks(SteveEntity steve, String command) {
        try {
            String systemPrompt = PromptBuilder.buildSystemPrompt();
            WorldKnowledge worldKnowledge = new WorldKnowledge(steve);
            String userPrompt = PromptBuilder.buildUserPrompt(steve, command, worldKnowledge);
            
            String provider = SteveConfig.AI_PROVIDER.get().toLowerCase();
            SteveMod.LOGGER.info("Requesting AI plan for Steve '{}' using {}: {}", steve.getSteveName(), provider, command);
            
            String response = getAIResponse(provider, systemPrompt, userPrompt);
            
            if (response == null) {
                SteveMod.LOGGER.error("Failed to get AI response for command: {}", command);
                return null;
            }            ResponseParser.ParsedResponse parsedResponse = ResponseParser.parseAIResponse(response);
            
            if (parsedResponse == null) {
                SteveMod.LOGGER.error("Failed to parse AI response");
                return null;
            }
            
            SteveMod.LOGGER.info("Plan: {} ({} tasks)", parsedResponse.getPlan(), parsedResponse.getTasks().size());
            
            return parsedResponse;
            
        } catch (Exception e) {
            SteveMod.LOGGER.error("Error planning tasks", e);
            return null;
        }
    }

    private String getAIResponse(String provider, String systemPrompt, String userPrompt) {
        String response = switch (provider) {
            case "deepseek" -> deepSeekClient.sendRequest(systemPrompt, userPrompt);
            case "groq" -> groqClient.sendRequest(systemPrompt, userPrompt);
            case "gemini" -> geminiClient.sendRequest(systemPrompt, userPrompt);
            case "openai" -> openAIClient.sendRequest(systemPrompt, userPrompt);
            default -> {
                SteveMod.LOGGER.warn("Unknown AI provider '{}', using DeepSeek", provider);
                yield deepSeekClient.sendRequest(systemPrompt, userPrompt);
            }
        };

        if (response == null && !provider.equals("deepseek")) {
            SteveMod.LOGGER.warn("{} failed, trying DeepSeek as fallback", provider);
            response = deepSeekClient.sendRequest(systemPrompt, userPrompt);
        }

        return response;
    }

    /**
     * Asynchronously plans tasks for Steve using the configured LLM provider.
     *
     * <p>This method returns immediately with a CompletableFuture, allowing the game thread
     * to continue without blocking. The actual LLM call is executed on a separate thread pool
     * with full resilience patterns (circuit breaker, retry, rate limiting, caching).</p>
     *
     * <p><b>Non-blocking:</b> Game thread is never blocked</p>
     * <p><b>Resilient:</b> Automatic retry, circuit breaker, fallback on failure</p>
     * <p><b>Cached:</b> Repeated prompts may hit cache (40-60% hit rate)</p>
     *
     * @param steve   The Steve entity making the request
     * @param command The user command to plan
     * @return CompletableFuture that completes with the parsed response, or null on failure
     */
    public CompletableFuture<ResponseParser.ParsedResponse> planTasksAsync(SteveEntity steve, String command) {
        try {
            String systemPrompt = PromptBuilder.buildSystemPrompt();
            WorldKnowledge worldKnowledge = new WorldKnowledge(steve);
            String userPrompt = PromptBuilder.buildUserPrompt(steve, command, worldKnowledge);

            String provider = SteveConfig.AI_PROVIDER.get().toLowerCase();
            SteveMod.LOGGER.info("[Async] Requesting AI plan for Steve '{}' using {}: {}",
                steve.getSteveName(), provider, command);

            // Build params map
            Map<String, Object> params = Map.of(
                "systemPrompt", systemPrompt,
                "model", configuredModel(provider),
                "maxTokens", configuredMaxTokens(provider),
                "temperature", configuredTemperature(provider)
            );

            // Select async client based on provider
            AsyncLLMClient client = getAsyncClient(provider);

            // Execute async request
            return client.sendAsync(userPrompt, params)
                .thenApply(response -> {
                    String content = response.getContent();
                    if (content == null || content.isEmpty()) {
                        SteveMod.LOGGER.error("[Async] Empty response from LLM");
                        return null;
                    }

                    ResponseParser.ParsedResponse parsed = ResponseParser.parseAIResponse(content);
                    if (parsed == null) {
                        SteveMod.LOGGER.error("[Async] Failed to parse AI response");
                        return null;
                    }

                    SteveMod.LOGGER.info("[Async] Plan received: {} ({} tasks, {}ms, {} tokens, cache: {})",
                        parsed.getPlan(),
                        parsed.getTasks().size(),
                        response.getLatencyMs(),
                        response.getTokensUsed(),
                        response.isFromCache());

                    return parsed;
                })
                .exceptionally(throwable -> {
                    SteveMod.LOGGER.error("[Async] Error planning tasks: {}", throwable.getMessage());
                    return null;
                });

        } catch (Exception e) {
            SteveMod.LOGGER.error("[Async] Error setting up task planning", e);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Returns the appropriate async client based on provider config.
     *
     * @param provider Provider name ("deepseek", "openai", "groq", "gemini")
     * @return Resilient async client
     */
    private AsyncLLMClient getAsyncClient(String provider) {
        return switch (provider) {
            case "deepseek" -> asyncDeepSeekClient;
            case "openai" -> asyncOpenAIClient;
            case "gemini" -> asyncGeminiClient;
            case "groq" -> asyncGroqClient;
            default -> {
                SteveMod.LOGGER.warn("[Async] Unknown provider '{}', using DeepSeek", provider);
                yield asyncDeepSeekClient;
            }
        };
    }

    String configuredModel(String provider) {
        if ("deepseek".equals(provider)) {
            return SteveConfig.DEEPSEEK_MODEL.get();
        }

        return SteveConfig.OPENAI_MODEL.get();
    }

    int configuredMaxTokens(String provider) {
        if ("deepseek".equals(provider)) {
            return SteveConfig.DEEPSEEK_MAX_TOKENS.get();
        }

        return SteveConfig.MAX_TOKENS.get();
    }

    double configuredTemperature(String provider) {
        if ("deepseek".equals(provider)) {
            return SteveConfig.DEEPSEEK_TEMPERATURE.get();
        }

        return SteveConfig.TEMPERATURE.get();
    }

    /**
     * Returns the LLM cache for monitoring.
     *
     * @return LLM cache instance
     */
    public LLMCache getLLMCache() {
        return llmCache;
    }

    /**
     * Checks if the specified provider's async client is healthy.
     *
     * @param provider Provider name
     * @return true if healthy (circuit breaker not OPEN)
     */
    public boolean isProviderHealthy(String provider) {
        return getAsyncClient(provider).isHealthy();
    }

    public boolean validateTask(Task task) {
        String action = task.getAction();
        
        return switch (action) {
            case "pathfind" -> task.hasParameters("x", "y", "z");
            case "mine" -> task.hasParameters("block", "quantity");
            case "place" -> task.hasParameters("block", "x", "y", "z");
            case "craft" -> task.hasParameters("item", "quantity");
            case "attack" -> task.hasParameters("target");
            case "follow" -> task.hasParameters("player");
            case "gather" -> task.hasParameters("resource", "quantity");
            case "build" -> task.hasParameters("structure", "blocks", "dimensions");
            default -> {
                SteveMod.LOGGER.warn("Unknown action type: {}", action);
                yield false;
            }
        };
    }

    public List<Task> validateAndFilterTasks(List<Task> tasks) {
        return tasks.stream()
            .filter(this::validateTask)
            .toList();
    }

    private interface AsyncClientFactory {
        AsyncLLMClient create();
    }
}
