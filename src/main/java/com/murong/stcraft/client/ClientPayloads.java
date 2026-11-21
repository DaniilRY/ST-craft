package com.murong.stcraft.client;

import com.murong.stcraft.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloads
{
    private ClientPayloads() {}

    public static void handleOpen(OpenChatPayload p, IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(
                new CompanionChatScreen(p.entityId(), p.name(), p.userName(), p.history())));
    }

    public static void handleDelta(ChatDeltaPayload p, IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof CompanionChatScreen cs && cs.entityId() == p.entityId())
            {
                cs.appendDelta(p.text());
            }
        });
    }

    public static void handleReply(ChatReplyPayload p, IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ChatLine line = p.line();
            if (mc.screen instanceof CompanionChatScreen cs && cs.entityId() == p.entityId())
            {
                cs.add(line);
            }
            else if (mc.player != null)
            {
                if (line.role() == ChatLine.COMPANION)
                {
                    Entity e = mc.level == null ? null : mc.level.getEntity(p.entityId());
                    String name = e != null ? e.getName().getString() : "?";
                    mc.player.sendSystemMessage(Component.literal("<" + name + "> " + line.text()));
                }

                else if (line.role() == ChatLine.ERROR)
                {
                    mc.player.sendSystemMessage(Component.translatable("stcraft.chat.error", Component.translatable(line.text(), line.args().toArray())).withStyle(ChatFormatting.RED));
                }
            }
        });
    }
}