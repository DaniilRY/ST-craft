package com.murong.stcraft.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ChatLine(int role, String text)
{
    public static final int PLAYER = 0, COMPANION = 1, SYSTEM = 2;

    public static final StreamCodec<ByteBuf, ChatLine> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatLine::role,
            ByteBufCodecs.STRING_UTF8, ChatLine::text,
            ChatLine::new);
}