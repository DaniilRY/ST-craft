package com.murong.stcraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Погибшие компаньоны, ожидающие возвращения (хранится на диске, переживает перезапуск). */
public class Graveyard extends SavedData {
    public record Entry(CompoundTag tag, long due, int mode, UUID follow, BlockPos spot, String spotDim) {}

    private final List<Entry> entries = new ArrayList<>();

    public static Graveyard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Graveyard::new, Graveyard::load), "stcraft_graveyard");
    }

    private static Graveyard load(CompoundTag tag, HolderLookup.Provider registries) {
        Graveyard g = new Graveyard();
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            g.entries.add(new Entry(c.getCompound("Entity"), c.getLong("Due"), c.getInt("Mode"),
                    c.hasUUID("Follow") ? c.getUUID("Follow") : null,
                    BlockPos.of(c.getLong("Spot")), c.getString("SpotDim")));
        }
        return g;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag c = new CompoundTag();
            c.put("Entity", e.tag());
            c.putLong("Due", e.due());
            c.putInt("Mode", e.mode());
            if (e.follow() != null) c.putUUID("Follow", e.follow());
            c.putLong("Spot", e.spot().asLong());
            c.putString("SpotDim", e.spotDim());
            list.add(c);
        }
        tag.put("Entries", list);
        return tag;
    }

    public void add(Entry e) {
        entries.add(e);
        setDirty();
    }

    public void tick(MinecraftServer server) {
        if (entries.isEmpty()) return;
        long now = server.overworld().getGameTime();
        boolean changed = false;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (now >= e.due() && respawn(server, e)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    private static boolean respawn(MinecraftServer server, Entry e) {
        ServerPlayer player = (e.mode() == CompanionEntity.FOLLOW && e.follow() != null)
                ? server.getPlayerList().getPlayer(e.follow()) : null;
        ServerLevel level = player != null ? player.serverLevel()
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.spotDim())));
        if (level == null) level = server.overworld();

        CompoundTag tag = e.tag().copy();
        tag.putFloat("Health", 20.0F);
        tag.putShort("DeathTime", (short) 0);
        tag.putShort("HurtTime", (short) 0);
        tag.putShort("Fire", (short) -1);
        tag.putFloat("FallDistance", 0.0F);
        tag.remove("active_effects");
        tag.remove("ActiveEffects");

        Entity created = EntityType.create(tag, level).orElse(null);
        if (!(created instanceof CompanionEntity c)) return true; // повреждённая запись, выбрасываем

        BlockPos pos = player != null ? player.blockPosition() : e.spot();
        level.getChunkAt(pos); // подгрузить чанк, если игрока там нет
        c.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, c.getYRot(), 0.0F);
        c.setHealth(c.getMaxHealth());
        level.addFreshEntity(c);
        if (player != null) c.teleportNear(player);
        c.onRespawned(server);
        return true;
    }
}