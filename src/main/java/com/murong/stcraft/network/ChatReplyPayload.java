package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatReplyPayload(int entityId, ChatLine line) implements CustomPacketPayload {
    public static final Type<ChatReplyPayload> TYPE = new Type<>(STCraft.id("chat_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatReplyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatReplyPayload::entityId,
            ChatLine.CODEC, ChatReplyPayload::line,
            ChatReplyPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}