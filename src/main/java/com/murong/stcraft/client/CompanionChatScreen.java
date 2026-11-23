package com.murong.stcraft.client;

import com.murong.stcraft.Tuning;
import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.network.ChatLine;
import com.murong.stcraft.network.ChatSendPayload;
import com.murong.stcraft.network.ChatStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CompanionChatScreen extends Screen {
    private static final int PAD = 6, GAP = 6;

    private record Block(List<FormattedCharSequence> lines) {}

    private final int entityId;
    private final String npcName, userName;
    private final List<ChatLine> lines;
    private final List<Block> blocks = new ArrayList<>();
    private StringBuilder streaming;
    private boolean waiting;
    private int scrollPx;
    private EditBox input;
    private Button sendButton;
    private int pingTimer;

    public CompanionChatScreen(int entityId, String name, String userName, List<ChatLine> history) {
        super(Component.literal(name));
        this.entityId = entityId;
        this.npcName = name;
        this.userName = userName;
        this.lines = new ArrayList<>(history);
    }

    public int entityId() { return entityId; }

    private int panelW() { return Math.min(width - 20, 460); }
    private int left() { return (width - panelW()) / 2; }
    private int top() { return 28; }
    private int bottom() { return height - 40; }

    @Override
    protected void init() {
        String keep = input == null ? "" : input.getValue();
        int l = left(), w = panelW();
        input = new EditBox(font, l, height - 28, w - 74, 20, Component.empty());
        input.setMaxLength(Tuning.CHAT_INPUT_MAX);
        input.setValue(keep);
        addRenderableWidget(input);
        setInitialFocus(input);
        sendButton = addRenderableWidget(Button.builder(Component.translatable("stcraft.chat.send"), b -> send()).bounds(l + w - 70, height - 28, 70, 20).build());
        sendButton.active = !waiting;
        if (minecraft != null && minecraft.player != null) {
            if (minecraft.player.isCreative() || minecraft.player.hasPermissions(2))
            {
                addRenderableWidget(Button.builder(Component.translatable("stcraft.chat.settings"), b -> openSkin()).bounds(width - 80, 6, 70, 18).build());
            }
        }
        rebuild();

        PacketDistributor.sendToServer(new ChatStatePayload(entityId, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (++pingTimer >= 20) {
            pingTimer = 0;
            PacketDistributor.sendToServer(new ChatStatePayload(entityId, true));
        }
    }

    @Override
    public void removed() {
        if (minecraft != null && minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new ChatStatePayload(entityId, false));
        }
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
        sendButton.active = false;
        scrollPx = 0;
        rebuild();
    }

    public void add(ChatLine l) {
        if (l.role() != ChatLine.PLAYER) {
            streaming = null;
            waiting = false;
            if (sendButton != null) sendButton.active = true;
        }
        lines.add(l);
        rebuild();
    }

    public void appendDelta(String d) {
        if (streaming == null) streaming = new StringBuilder();
        streaming.append(d);
        rebuild();
    }

    // ---------- вёрстка ----------

    private void rebuild() {
        if (font == null) return;
        blocks.clear();
        int w = panelW() - 2 * PAD;
        for (ChatLine l : lines) blocks.add(new Block(font.split(format(l), w)));
        if (streaming != null) {
            blocks.add(new Block(font.split(format(new ChatLine(ChatLine.COMPANION, streaming.toString())), w)));
        } else if (waiting) {
            blocks.add(new Block(font.split(Component.translatable("stcraft.chat.thinking")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), w)));
        }
    }

    private Component format(ChatLine l) {
        Object[] a = l.args().toArray();
        String player_name = Component.translatable("stcraft.chat.you") + " (" + userName + ")";
        return switch (l.role()) {
            case ChatLine.PLAYER -> speaker(userName, ChatFormatting.GOLD, l.text());
            case ChatLine.COMPANION -> speaker(npcName, ChatFormatting.AQUA, l.text());
            case ChatLine.ERROR -> Component.translatable("stcraft.chat.error",
                    Component.translatable(l.text(), a)).withStyle(ChatFormatting.RED);
            case ChatLine.INFO -> Component.literal("— ").append(Component.translatable(l.text(), a))
                    .append(" —").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC);
            default -> Component.literal("— ").append(Component.translatable(l.text(), a))
                    .append(" —").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC);
        };
    }

    /** Цветной только ник; текст реплики без цвета, с разметкой *курсив* и **жирный**. */
    private static Component speaker(String name, ChatFormatting color, String text) {
        return Component.empty()
                .append(Component.literal(name).withStyle(color))
                .append(Component.literal(": "))
                .append(markdown(text));
    }

    private static MutableComponent markdown(String s) {
        MutableComponent out = Component.empty();
        StringBuilder buf = new StringBuilder();
        boolean italic = false, bold = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '*') {
                flush(out, buf, italic, bold);
                if (i + 1 < s.length() && s.charAt(i + 1) == '*') { bold = !bold; i++; }
                else italic = !italic;
            } else {
                buf.append(ch);
            }
        }
        flush(out, buf, italic, bold);
        return out;
    }

    private static void flush(MutableComponent out, StringBuilder buf, boolean italic, boolean bold) {
        if (buf.isEmpty()) return;
        Style st = Style.EMPTY.withItalic(italic).withBold(bold);
        if (italic) st = st.withColor(ChatFormatting.GRAY);
        out.append(Component.literal(buf.toString()).withStyle(st));
        buf.setLength(0);
    }

    // ---------- ввод ----------

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
        scrollPx = Math.max(0, scrollPx + (int) (sy * 20));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---------- отрисовка ----------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int l = left(), r = l + panelW(), t = top(), b = bottom();
        g.fill(l, t, r, b, 0xB0101015);
        //g.drawCenteredString(font, Component.literal(npcName).withStyle(ChatFormatting.AQUA), width / 2, 12, 0xFFFFFFFF);

        MutableComponent header = Component.literal(npcName).withStyle(ChatFormatting.AQUA);
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof CompanionEntity c) {
            if (c.isFollowing()) {
                header.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("stcraft.chat.status.following").withStyle(ChatFormatting.GREEN));
            } else if (c.isStaying()) {
                header.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("stcraft.chat.status.staying").withStyle(ChatFormatting.YELLOW));
            }
        }
        g.drawCenteredString(font, header, width / 2, 12, 0xFFFFFFFF);

        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity le)
        {
            int hp = (int) Math.ceil(le.getHealth());
            int max = (int) Math.ceil(le.getMaxHealth());
            g.drawString(font, Component.translatable("stcraft.chat.health", hp, max).withStyle(ChatFormatting.RED), left() + PAD, 12, 0xFFFFFFFF);
        }

        int lh = font.lineHeight + 1;
        int contentH = 0;
        for (Block bl : blocks) contentH += bl.lines().size() * lh;
        if (!blocks.isEmpty()) contentH += GAP * (blocks.size() - 1);
        int viewH = b - t - 2 * PAD;
        scrollPx = Math.min(scrollPx, Math.max(0, contentH - viewH));

        g.enableScissor(l, t, r, b);
        int y = b - PAD - contentH + scrollPx;
        for (Block bl : blocks) {
            for (FormattedCharSequence s : bl.lines()) {
                g.drawString(font, s, l + PAD, y, 0xFFFFFFFF);
                y += lh;
            }
            y += GAP;
        }
        g.disableScissor();
    }
}