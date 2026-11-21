package com.murong.stcraft.client;

import com.murong.stcraft.Tuning;
import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.network.ChatLine;
import com.murong.stcraft.network.ChatSendPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CompanionChatScreen extends Screen {
    private record Row(FormattedCharSequence text, int color) {}

    private final int entityId;
    private final String npcName;
    private final List<ChatLine> lines;
    private EditBox input;
    private int scroll;
    private boolean waiting;

    public CompanionChatScreen(int entityId, String name, List<ChatLine> history) {
        super(Component.literal(name));
        this.entityId = entityId;
        this.npcName = name;
        this.lines = new ArrayList<>(history);
    }

    public int entityId() { return entityId; }

    @Override
    protected void init() {
        input = new EditBox(font, 10, height - 26, width - 20, 18, Component.empty());
        input.setMaxLength(Tuning.CHAT_INPUT_MAX);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Скин"), b -> openSkin())
                .bounds(width - 60, 6, 50, 18).build());
    }

    private void openSkin() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof CompanionEntity c) {
            minecraft.setScreen(new CompanionSkinScreen(c));
        }
    }

    private void send() {
        String text = input.getValue().trim();
        if (text.isEmpty() || waiting) return;
        lines.add(new ChatLine(ChatLine.PLAYER, text));
        PacketDistributor.sendToServer(new ChatSendPayload(entityId, text));
        input.setValue("");
        waiting = true;
        scroll = 0;
    }

    public void add(ChatLine l) {
        lines.add(l);
        if (l.role() != ChatLine.PLAYER) waiting = false;
        scroll = 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.max(0, scroll + (int) Math.signum(sy));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawString(font, title, 10, 10, 0xFFFFFF);

        List<Row> rows = new ArrayList<>();
        int w = width - 20;
        for (ChatLine l : lines) {
            String prefix = switch (l.role()) {
                case ChatLine.PLAYER -> "Вы: ";
                case ChatLine.COMPANION -> npcName + ": ";
                default -> "";
            };
            int color = switch (l.role()) {
                case ChatLine.PLAYER -> 0xFFFFFF;
                case ChatLine.COMPANION -> 0xFFD27F;
                default -> 0x55FF55;
            };
            for (FormattedCharSequence s : font.split(Component.literal(prefix + l.text()), w)) {
                rows.add(new Row(s, color));
            }
        }
        if (waiting) rows.add(new Row(Component.literal("печатает…").getVisualOrderText(), 0xAAAAAA));

        int y = height - 40;
        int skip = Math.min(scroll, Math.max(0, rows.size() - 1));
        for (int i = rows.size() - 1 - skip; i >= 0 && y > 30; i--) {
            g.drawString(font, rows.get(i).text(), 10, y, rows.get(i).color());
            y -= 10;
        }
    }
}