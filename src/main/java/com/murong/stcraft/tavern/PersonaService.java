package com.murong.stcraft.tavern;

import net.minecraft.server.level.ServerPlayer;

public final class PersonaService {
    private PersonaService() {}

    public static final String KEY = "stcraft_persona";

    public static String selected(ServerPlayer sp)
    {
        return sp.getPersistentData().getString(KEY);
    }

    public static Persona pick(TavernClient.Settings st, String selected, String nick)
    {
        return st.persona(selected).orElse(new Persona("", nick, ""));
    }
}