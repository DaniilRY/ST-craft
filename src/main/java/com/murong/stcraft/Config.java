package com.murong.stcraft;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config
{
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STREAMING = BUILDER
            .comment("Stream companion replies as they are generated. Turn off if your Tavern backend does not stream.")
            .define("streaming", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}