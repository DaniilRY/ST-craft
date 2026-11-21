package com.murong.stcraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record ChatLine(int role, String text, List<String> args)
{
    public static final int PLAYER = 0, COMPANION = 1, SYSTEM = 2, ERROR = 3, INFO = 4;

    public ChatLine(int role, String text)
    {
        this(role, text, List.of());
    }

    public static final StreamCodec<ByteBuf, ChatLine> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatLine::role,
            ByteBufCodecs.STRING_UTF8, ChatLine::text,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ChatLine::args,
            ChatLine::new);
}