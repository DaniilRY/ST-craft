package com.murong.stcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TAVERN_URL;
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue STREAMING;
    public static final ModConfigSpec.IntValue MIN_REPLY_TOKENS;
    public static final ModConfigSpec.IntValue HISTORY_MAX;
    public static final ModConfigSpec.IntValue PROMPT_HISTORY;
    public static final ModConfigSpec.IntValue CHAT_INPUT_MAX;
    public static final ModConfigSpec.DoubleValue CHAT_RANGE;
    public static final ModConfigSpec.IntValue RESPAWN_SECONDS;

    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("tavern");
        TAVERN_URL = BUILDER.comment("Address of the SillyTavern server").define("url", "http://127.0.0.1:8000");
        TIMEOUT_SECONDS = BUILDER.comment("Request timeout in seconds").defineInRange("timeoutSeconds", 120, 5, 600);
        STREAMING = BUILDER.comment("Stream companion replies as they are generated. Turn off if your Tavern backend does not stream.").define("streaming", true);
        MIN_REPLY_TOKENS = BUILDER.comment("Lower bound for max_tokens, so reasoning models still have room to answer").defineInRange("minReplyTokens", 1024, 64, 32768);
        BUILDER.pop();

        BUILDER.push("chat");
        HISTORY_MAX = BUILDER.comment("Messages kept per player and companion").defineInRange("historyMax", 100, 10, 500);
        PROMPT_HISTORY = BUILDER.comment("Messages sent to the model with each request").defineInRange("promptHistory", 50, 2, 200);
        CHAT_INPUT_MAX = BUILDER.comment("Maximum length of one chat message in characters. Keep it equal on the client and the server").defineInRange("chatInputMax", 1000, 50, 4000);
        CHAT_RANGE = BUILDER.comment("How far (in blocks) the companion can be from you while you chat with him").defineInRange("chatRange", 12.0, 3.0, 128.0);
        BUILDER.pop();

        BUILDER.push("character");
        RESPAWN_SECONDS = BUILDER.comment("Seconds until a dead companion comes back (if respawn is enabled for him)").defineInRange("respawnSeconds", 60, 5, 3600);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}