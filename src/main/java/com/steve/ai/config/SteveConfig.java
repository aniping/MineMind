package com.steve.ai.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class SteveConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> AI_PROVIDER;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> OPENAI_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_BASE_URL;
    public static final ForgeConfigSpec.ConfigValue<String> DEEPSEEK_MODEL;
    public static final ForgeConfigSpec.IntValue DEEPSEEK_MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue DEEPSEEK_TEMPERATURE;
    public static final ForgeConfigSpec.IntValue MAX_TOKENS;
    public static final ForgeConfigSpec.DoubleValue TEMPERATURE;
    public static final ForgeConfigSpec.IntValue ACTION_TICK_DELAY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CHAT_RESPONSES;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_STEVES;
    public static final ForgeConfigSpec.BooleanValue MINEMIND_AUTONOMOUS_MODE_DEFAULT;
    public static final ForgeConfigSpec.BooleanValue MINEMIND_CHAT_GUIDANCE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MINEMIND_COMMUNITY_MODE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MINEMIND_LONG_TERM_MEMORY_ENABLED;
    public static final ForgeConfigSpec.IntValue MINEMIND_THINK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue MINEMIND_MAX_PLANNING_STEPS;
    public static final ForgeConfigSpec.BooleanValue MINEMIND_USE_LLM_PLANNER;
    public static final ForgeConfigSpec.IntValue MINEMIND_CHAT_RESPONSE_COOLDOWN_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("AI API Configuration").push("ai");
        
        AI_PROVIDER = builder
            .comment("AI provider to use: 'deepseek', 'groq', 'openai', or 'gemini'")
            .define("provider", "deepseek");
        
        builder.pop();

        builder.comment("DeepSeek API Configuration").push("deepseek");

        DEEPSEEK_API_KEY = builder
            .comment("DeepSeek API key. Do not commit real keys.")
            .define("apiKey", "");

        DEEPSEEK_BASE_URL = builder
            .comment("DeepSeek OpenAI-compatible API base URL")
            .define("baseUrl", "https://api.deepseek.com");

        DEEPSEEK_MODEL = builder
            .comment("DeepSeek model to use, such as deepseek-chat or deepseek-reasoner")
            .define("model", "deepseek-chat");

        DEEPSEEK_MAX_TOKENS = builder
            .comment("Maximum tokens per DeepSeek request")
            .defineInRange("maxTokens", 4000, 100, 65536);

        DEEPSEEK_TEMPERATURE = builder
            .comment("DeepSeek temperature")
            .defineInRange("temperature", 0.7, 0.0, 2.0);

        builder.pop();

        builder.comment("Optional legacy provider API Configuration (used only when provider is openai, groq, or gemini)").push("openai");
        
        OPENAI_API_KEY = builder
            .comment("Optional legacy provider API key. DeepSeek uses the [deepseek] section.")
            .define("apiKey", "");
        
        OPENAI_MODEL = builder
            .comment("Optional model for the openai provider. Set this explicitly before using provider=openai.")
            .define("model", "");
        
        MAX_TOKENS = builder
            .comment("Maximum tokens per API request")
            .defineInRange("maxTokens", 8000, 100, 65536);
        
        TEMPERATURE = builder
            .comment("Temperature for AI responses (0.0-2.0, lower is more deterministic)")
            .defineInRange("temperature", 0.7, 0.0, 2.0);
        
        builder.pop();

        builder.comment("Steve Behavior Configuration").push("behavior");
        
        ACTION_TICK_DELAY = builder
            .comment("Ticks between action checks (20 ticks = 1 second)")
            .defineInRange("actionTickDelay", 20, 1, 100);
        
        ENABLE_CHAT_RESPONSES = builder
            .comment("Allow Steves to respond in chat")
            .define("enableChatResponses", true);
        
        MAX_ACTIVE_STEVES = builder
            .comment("Maximum number of Steves that can be active simultaneously")
            .defineInRange("maxActiveSteves", 10, 1, 50);
        
        builder.pop();

        builder.comment("MineMind Configuration (all new behavior is disabled by default)").push("minemind");

        MINEMIND_AUTONOMOUS_MODE_DEFAULT = builder
            .comment("Whether newly spawned Steves start with MineMind autonomous mode enabled")
            .define("autonomousModeDefault", false);

        MINEMIND_CHAT_GUIDANCE_ENABLED = builder
            .comment("Allow MineMind chat guidance handling when implemented")
            .define("enableChatGuidance", false);

        MINEMIND_COMMUNITY_MODE_ENABLED = builder
            .comment("Allow MineMind multi-agent community behavior when implemented")
            .define("enableCommunityMode", false);

        MINEMIND_LONG_TERM_MEMORY_ENABLED = builder
            .comment("Allow MineMind long-term memory persistence when implemented")
            .define("enableLongTermMemory", false);

        MINEMIND_THINK_INTERVAL_TICKS = builder
            .comment("Ticks between MineMind autonomous thinking cycles")
            .defineInRange("thinkIntervalTicks", 200, 20, 72000);

        MINEMIND_MAX_PLANNING_STEPS = builder
            .comment("Maximum task steps a MineMind planner may emit")
            .defineInRange("maxPlanningSteps", 3, 1, 20);

        MINEMIND_USE_LLM_PLANNER = builder
            .comment("Use LLM planning for MineMind autonomous mode instead of rule planning when implemented")
            .define("useLlmPlanner", false);

        MINEMIND_CHAT_RESPONSE_COOLDOWN_TICKS = builder
            .comment("Minimum ticks between MineMind chat replies from the same Steve")
            .defineInRange("chatResponseCooldownTicks", 100, 20, 72000);

        builder.pop();

        SPEC = builder.build();
    }
}
