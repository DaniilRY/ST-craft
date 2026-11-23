package com.murong.stcraft.client;

import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.network.SetSkinPayload;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CompanionSkinScreen extends Screen
{
    private final CompanionEntity companion;
    private final List<String> skins = new ArrayList<>();
    private int index;
    private boolean slim;
    private boolean child;
    private boolean invulnerable;
    private boolean attackMobs;
    private SkinDropdown dropdown;

    public CompanionSkinScreen(CompanionEntity companion)
    {
        super(Component.translatable("stcraft.skin.title", companion.getName()));
        this.companion = companion;
    }

    @Override
    protected void init()
    {
        skins.clear();
        skins.add("");
        skins.addAll(SkinCache.list());
        index = Math.max(0, skins.indexOf(companion.getSkinSource()));
        slim = companion.isSlim();
        child = companion.isBaby();
        invulnerable = companion.isFriendlyInvulnerable();
        attackMobs = companion.isAttackingMobs();

        int cx = width / 2;

        // 1. Список со скинами
        dropdown = new SkinDropdown(font, cx - 100, 28, 200, 20, skins, index,
                s -> s.isEmpty() ? Component.translatable("stcraft.skin.default").getString() : s,
                i -> index = i);
        addRenderableWidget(dropdown);

        // 2. Открыть папку со скинами
        addRenderableWidget(Button.builder(Component.translatable("stcraft.skin.open_folder"),
                        b -> Util.getPlatform().openFile(SkinCache.DIR.toFile()))
                .bounds(cx - 100, 52, 200, 20).build());

        // 3. Тип рук
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.translatable("stcraft.skin.model.slim"),
                        Component.translatable("stcraft.skin.model.wide"))
                .displayOnlyValue()
                .withInitialValue(slim)
                .create(cx - 100, 76, 200, 20, Component.translatable("stcraft.skin.model"),
                        (btn, v) -> slim = v));

        // 4. Модель ребёнка
        addRenderableWidget(CycleButton.onOffBuilder(child)
                .create(cx - 100, 100, 200, 20, Component.translatable("stcraft.skin.child"),
                        (btn, v) -> child = v));

        // 5. Неуязвимость
        addRenderableWidget(CycleButton.onOffBuilder(invulnerable)
                .create(cx - 100, 124, 200, 20, Component.translatable("stcraft.skin.invulnerable"),
                        (btn, v) -> invulnerable = v));

        // 6. Атаковать мобов
        addRenderableWidget(CycleButton.onOffBuilder(attackMobs)
                .create(cx - 100, 148, 200, 20, Component.translatable("stcraft.skin.attack_mobs"),
                        (btn, v) -> attackMobs = v));

        // 7. Применить / Отмена
        addRenderableWidget(Button.builder(Component.translatable("stcraft.skin.apply"), b -> {
            PacketDistributor.sendToServer(new SetSkinPayload(
                    companion.getId(), skins.get(index), slim, child, invulnerable, attackMobs));
            onClose();
        }).bounds(cx - 100, 176, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("stcraft.skin.cancel"), b -> onClose())
                .bounds(cx + 2, 176, 98, 20).build());
    }

    private void shift(int d) {
        index = Math.floorMod(index + d, skins.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (dropdown != null && dropdown.isOpen()) return dropdown.mouseClicked(mx, my, button);
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (dropdown != null && dropdown.isOpen() && dropdown.mouseScrolled(mx, my, sx, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && dropdown != null && dropdown.isOpen()) {
            dropdown.close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}