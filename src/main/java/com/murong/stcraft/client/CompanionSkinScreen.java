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

import java.util.ArrayList;
import java.util.List;

public class CompanionSkinScreen extends Screen {
    private final CompanionEntity companion;
    private final List<String> skins = new ArrayList<>();
    private int index;
    private boolean slim;

    public CompanionSkinScreen(CompanionEntity companion) {
        super(Component.literal("Скин: " + companion.getName().getString()));
        this.companion = companion;
    }

    @Override
    protected void init() {
        skins.clear();
        skins.add("");
        skins.addAll(SkinCache.list());
        index = Math.max(0, skins.indexOf(companion.getSkinSource()));
        slim = companion.isSlim();

        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> shift(-1))
                .bounds(cx - 100, 60, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> shift(1))
                .bounds(cx + 80, 60, 20, 20).build());
        addRenderableWidget(CycleButton.booleanBuilder(
                        Component.literal("Alex (тонкие руки)"), Component.literal("Steve (широкие руки)"))
                .displayOnlyValue()
                .withInitialValue(slim)
                .create(cx - 100, 90, 200, 20, Component.literal("Модель"), (btn, v) -> slim = v));
        addRenderableWidget(Button.builder(Component.literal("Открыть папку со скинами"),
                        b -> Util.getPlatform().openFile(SkinCache.DIR.toFile()))
                .bounds(cx - 100, 120, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Применить"), b -> {
            PacketDistributor.sendToServer(
                    new SetSkinPayload(companion.getId(), skins.get(index), slim));
            onClose();
        }).bounds(cx - 100, 150, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(cx + 2, 150, 98, 20).build());
    }

    private void shift(int d) {
        index = Math.floorMod(index + d, skins.size());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);
        String label = skins.get(index).isEmpty() ? "по умолчанию" : skins.get(index);
        g.drawCenteredString(font, label, width / 2, 66, 0xFFFFFF);
    }
}