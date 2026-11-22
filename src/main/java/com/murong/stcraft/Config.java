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
    public static final ModConfigSpec.BooleanValue TELEPORT_HOME;
    public static final ModConfigSpec.DoubleValue HOME_TELEPORT_DISTANCE;

    static final ModConfigSpec SPEC;

    static {
        BUILDER.push("tavern");
        TAVERN_URL = BUILDER.comment("Address of the SillyTavern server").define("url", "http://127.0.0.1:8000");
        TIMEOUT_SECONDS = BUILDER.comment("Request timeout in seconds").defineInRange("timeoutSeconds", 120, 5, 600);
        STREAMING = BUILDER.comment("Stream companion replies as they are generated. Turn off if your Tavern backend does not stream.").define("streaming", true);
        MIN_REPLY_TOKENS = BUILDER.comment("Lower bound for max_tokens, so reasoning models still have room to answer").defineInRange("minReplyTokens", 1024, 64, 32768);
        BUILDER.pop();

        BUILDER.push("chat");
        HISTORY_MAX = BUILDER.comment("Messages kept per player and companion").defineInRange("historyMax", 60, 10, 500);
        PROMPT_HISTORY = BUILDER.comment("Messages sent to the model with each request").defineInRange("promptHistory", 30, 2, 200);
        BUILDER.pop();

        BUILDER.push("character");
        TELEPORT_HOME = BUILDER.comment("A released companion teleports home when he is too far from it").define("teleportHomeOnRelease", false);
        HOME_TELEPORT_DISTANCE = BUILDER.comment("Distance from home (blocks) after which a free companion teleports home").defineInRange("homeTeleportDistance", 24.0, 4.0, 256.0);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}