package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record OpenChatPayload(int entityId, String name, String userName, List<ChatLine> history) implements CustomPacketPayload
{
    public static final Type<OpenChatPayload> TYPE = new Type<>(STCraft.id("open_chat"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenChatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenChatPayload::entityId,
            ByteBufCodecs.STRING_UTF8, OpenChatPayload::name,
            ByteBufCodecs.STRING_UTF8, OpenChatPayload::userName,
            ChatLine.CODEC.apply(ByteBufCodecs.list()), OpenChatPayload::history,
            OpenChatPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}