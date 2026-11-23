package com.murong.stcraft.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Метательное и стрелковое оружие из других модов: предмет и фабрика его снаряда. */
public final class CompanionRanged
{
    private CompanionRanged() {}

    @FunctionalInterface
    public interface Factory
    {
        Projectile create(Level level, LivingEntity shooter, ItemStack stack);
    }

    public record Entry(Factory factory, float speed, int interval, float gravity) {}

    private static final Map<Item, Entry> ENTRIES = new ConcurrentHashMap<>();

    public static void register(Item item, Factory factory) {
        register(item, factory, 2.0F, 30, 0.05F);
    }

    public static void register(Item item, Factory factory, float speed, int interval) {
        register(item, factory, speed, interval, 0.05F);
    }

    /** gravity: падение снаряда за тик (стрела и трезубец 0.05, у модовых может быть другое). */
    public static void register(Item item, Factory factory, float speed, int interval, float gravity) {
        ENTRIES.put(item, new Entry(factory, speed, interval, gravity));
    }

    public static Entry get(Item item) { return ENTRIES.get(item); }
}