package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatStatePayload(int entityId, boolean open) implements CustomPacketPayload {
    public static final Type<ChatStatePayload> TYPE = new Type<>(STCraft.id("chat_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChatStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChatStatePayload::entityId,
            ByteBufCodecs.BOOL, ChatStatePayload::open,
            ChatStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}