package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatSendPayload(int entityId, String text) implements CustomPacketPayload {
    public static final Type<ChatSendPayload> TYPE = new Type<>(STCraft.id("chat_send"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatSendPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatSendPayload::entityId,
            ByteBufCodecs.STRING_UTF8, ChatSendPayload::text,
            ChatSendPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}