package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatDeltaPayload(int entityId, String text) implements CustomPacketPayload
{
    public static final Type<ChatDeltaPayload> TYPE = new Type<>(STCraft.id("chat_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatDeltaPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatDeltaPayload::entityId,
            ByteBufCodecs.STRING_UTF8, ChatDeltaPayload::text,
            ChatDeltaPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}