package com.murong.stcraft.entity;

import com.murong.stcraft.STCraft;
import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.entity.CompanionEquipContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public class CompanionMenu extends AbstractContainerMenu {
    // 0..26 хранилище, 27..32 экипировка, 33..68 инвентарь игрока и хотбар
    private static final int EQUIP_START = 27, PLAYER_START = 33, END = 69;

    private final CompanionEntity companion;

    /** Клиентская сторона: id моба приходит в дополнительных данных. */
    public CompanionMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (CompanionEntity) Objects.requireNonNull(
                inv.player.level().getEntity(buf.readVarInt()), "companion not found"));
    }

    public CompanionMenu(int id, Inventory inv, CompanionEntity companion) {
        super(STCraft.COMPANION_MENU.get(), id);
        this.companion = companion;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(companion.inventory, r * 9 + c, 8 + c * 18, 8 + r * 18));
            }
        }

        CompanionEquipContainer eq = new CompanionEquipContainer(companion);
        int[] x = {8, 26, 44, 62, 134, 152};
        for (int i = 0; i < 6; i++) addSlot(new EquipSlot(eq, i, x[i], 62));

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
    }

    private static class EquipSlot extends Slot {
        private final EquipmentSlot type;

        EquipSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.type = CompanionEquipContainer.SLOTS[index];
        }

        @Override
        public boolean mayPlace(ItemStack s) {
            return !type.isArmor() || CompanionEntity.slotFor(s) == type;
        }

        @Override
        public int getMaxStackSize() {
            return type.isArmor() ? 1 : super.getMaxStackSize();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < PLAYER_START) { // от компаньона к игроку
            if (!moveItemStackTo(stack, PLAYER_START, END, true)) return ItemStack.EMPTY;
        } else {                    // от игрока: сначала в свой слот экипировки, иначе в инвентарь
            boolean moved = false;
            EquipmentSlot es = CompanionEntity.slotFor(stack);
            if (es != null) {
                int target = switch (es) {
                    case HEAD -> EQUIP_START;
                    case CHEST -> EQUIP_START + 1;
                    case LEGS -> EQUIP_START + 2;
                    case FEET -> EQUIP_START + 3;
                    case OFFHAND -> EQUIP_START + 4;
                    default -> EQUIP_START + 5;
                };
                if (!slots.get(target).hasItem()) moved = moveItemStackTo(stack, target, target + 1, false);
            }
            if (!moved && !moveItemStackTo(stack, 0, EQUIP_START, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return companion.isAlive() && player.distanceToSqr(companion) < 64.0;
    }
}
