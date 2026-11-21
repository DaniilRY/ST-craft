package com.murong.stcraft;

public final class Tuning
{
    private Tuning() {}

    public static final double FOLLOW_START = 5.0;
    public static final double FOLLOW_STOP = 2.5;
    public static final double RUN_DISTANCE = 10.0;
    public static final double TELEPORT_DISTANCE = 20.0;
    public static final double HOME_RADIUS = 12.0;
    public static final double COMBAT_LEASH = 24.0;
    public static final int REGEN_DELAY_TICKS = 5 * 20;
    public static final int RESPAWN_TICKS = 60 * 20;

    public static final String TAVERN_URL = "http://127.0.0.1:8000";
    public static final int TAVERN_TIMEOUT_SECONDS = 120;

    public static final int HISTORY_MAX = 60;
    public static final int PROMPT_HISTORY = 30;
    public static final int CHAT_INPUT_MAX = 500;
    public static final String CHAT_HINT = "This is a conversation inside Minecraft: {{char}} is a companion standing next to {{user}} in the game world. Keep replies short, 1-3 sentences, and stay in character.";
}
