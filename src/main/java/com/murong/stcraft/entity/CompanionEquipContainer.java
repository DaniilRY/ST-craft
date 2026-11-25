package com.murong.stcraft.entity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CompanionEquipContainer implements Container {
    /** Порядок слотов в окне: шлем, нагрудник, поножи, ботинки, левая рука, правая рука. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND
    };

    private final CompanionEntity c;

    public CompanionEquipContainer(CompanionEntity c) { this.c = c; }

    @Override public int getContainerSize() { return SLOTS.length; }

    @Override
    public boolean isEmpty() {
        for (EquipmentSlot s : SLOTS) if (!c.getItemBySlot(s).isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int i) { return c.getItemBySlot(SLOTS[i]); }

    @Override
    public ItemStack removeItem(int i, int amount) {
        ItemStack cur = c.getItemBySlot(SLOTS[i]);
        if (cur.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = cur.split(amount);
        if (cur.isEmpty()) c.setItemSlot(SLOTS[i], ItemStack.EMPTY);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack cur = c.getItemBySlot(SLOTS[i]);
        c.setItemSlot(SLOTS[i], ItemStack.EMPTY);
        return cur;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        c.setItemSlot(SLOTS[i], stack);
        c.setDropChance(SLOTS[i], 1.0F); // при смерти выпадает всё
    }

    @Override public void setChanged() {}

    @Override
    public boolean stillValid(Player p) {
        return c.isAlive() && p.distanceToSqr(c) < 64.0;
    }

    @Override
    public void clearContent() {
        for (EquipmentSlot s : SLOTS) c.setItemSlot(s, ItemStack.EMPTY);
    }
}