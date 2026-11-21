package com.murong.stcraft.tavern;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class TavernException extends RuntimeException
{
    private final String key;
    private final List<String> args;

    public TavernException(String key, String... args)
    {
        super(key + " " + List.of(args));
        this.key = key;
        this.args = List.of(args);
    }

    public String key() { return key; }
    public List<String> args() { return args; }
    public Component component() { return Component.translatable(key, args.toArray()); }

    public static TavernException find(Throwable t)
    {
        for (Throwable x = t; x != null; x = x.getCause())
        {
            if (x instanceof TavernException te) return te;
        }
        return null;
    }

    public static TavernException of(Throwable t)
    {
        TavernException te = find(t);
        if (te != null) return te;
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return new TavernException("stcraft.error.generic",
                r.getMessage() != null ? r.getMessage() : r.toString());
    }
}