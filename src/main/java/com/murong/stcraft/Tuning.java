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

    public static final int MIN_REPLY_TOKENS = 1024;
    public static final String MARKER_HINT = "You can walk. First answer what {{user}} said, in character. Only when {{user}} asks you to come along, or to stay or wait, add one service tag at the very end of your reply: [FOLLOW] if you agree to follow {{user}}, [STAY] if you stay here or agree to wait. If you refuse, or the message is not about moving, add no tag. You may also decide to stop following on your own. Never mention the tags. Example: \"Sure, lead the way. [FOLLOW]\"";
    //public static final String MARKER_HINT = "Service tags: when you agree to follow {{user}}, end your reply with [FOLLOW]. When you decide to stay here, or {{user}} asks you to wait, end your reply with [STAY]. If you refuse or nothing changes, write no tag. You may also stop following on your own, for example when offended. Never mention these tags or explain them.";
    public static final String STATE_FOLLOWING = "Current state: you are following {{user}}.";
    public static final String STATE_STAYING = "Current state: you are staying here and not following anyone.";
}
