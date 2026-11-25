package com.murong.stcraft.network;

import com.murong.stcraft.STCraft;
import com.murong.stcraft.entity.CompanionEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetSkinPayload(int entityId, String skin, boolean slim, boolean child, boolean invulnerable, boolean attackMobs, boolean respawn) implements CustomPacketPayload
{
    public static final Type<SetSkinPayload> TYPE = new Type<>(STCraft.id("set_skin"));

    /*
    public static final StreamCodec<RegistryFriendlyByteBuf, SetSkinPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, SetSkinPayload::skin,
            ByteBufCodecs.BOOL, SetSkinPayload::slim,
            ByteBufCodecs.BOOL, SetSkinPayload::child,
            ByteBufCodecs.BOOL, SetSkinPayload::invulnerable,
            ByteBufCodecs.BOOL, SetSkinPayload::attackMobs,
            ByteBufCodecs.BOOL, SetSkinPayload::respawn,
            SetSkinPayload::new);*/

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSkinPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.entityId());
                        buf.writeUtf(payload.skin());
                        buf.writeBoolean(payload.slim());
                        buf.writeBoolean(payload.child());
                        buf.writeBoolean(payload.invulnerable());
                        buf.writeBoolean(payload.attackMobs());
                        buf.writeBoolean(payload.respawn());
                    },
                    buf -> new SetSkinPayload(
                            buf.readVarInt(),
                            buf.readUtf(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetSkinPayload p, IPayloadContext ctx)
    {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.isCreative()) return;
            Entity e = player.level().getEntity(p.entityId());
            if (!(e instanceof CompanionEntity c) || c.distanceToSqr(player) > 64) return;
            String s = p.skin();
            if (!s.isEmpty() && (s.length() > 64 || !s.endsWith(".png")
                    || s.contains("/") || s.contains("\\") || s.contains(".."))) return;
            c.setSkinSource(s);
            c.setSlim(p.slim());
            c.setChild(p.child());
            c.setFriendlyInvulnerable(p.invulnerable());
            c.setAttackMobs(p.attackMobs());
            c.setRespawnEnabled(p.respawn());
        });
    }
}
