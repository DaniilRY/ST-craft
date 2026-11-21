package com.murong.stcraft.client;

import com.murong.stcraft.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloads {
    private ClientPayloads() {}

    public static void handleOpen(OpenChatPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new CompanionChatScreen(p.entityId(), p.name(), p.history())));
    }

    public static void handleReply(ChatReplyPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof CompanionChatScreen cs && cs.entityId() == p.entityId()) {
                cs.add(p.line());
            } else if (mc.player != null && p.line().role() == ChatLine.COMPANION) {
                Entity e = mc.level == null ? null : mc.level.getEntity(p.entityId());
                String name = e != null ? e.getName().getString() : "?";
                mc.player.sendSystemMessage(Component.literal("<" + name + "> " + p.line().text()));
            }
        });
    }
}