package com.murong.stcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class SkinDropdown extends AbstractWidget {
    private static final int ROW = 14, VISIBLE = 6;
    private static final ResourceLocation BTN = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BTN_HOVER = ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private final Font font;
    private final List<String> items;
    private final Function<String, String> labelOf;
    private final Consumer<Integer> onChange;
    private int selected;
    private boolean open;
    private int scroll;

    public SkinDropdown(Font font, int x, int y, int w, int h, List<String> items, int selected,
                        Function<String, String> labelOf, Consumer<Integer> onChange) {
        super(x, y, w, h, Component.empty());
        this.font = font;
        this.items = items;
        this.selected = selected;
        this.labelOf = labelOf;
        this.onChange = onChange;
    }

    public boolean isOpen() { return open; }
    public void close() { open = false; }

    private int rows() { return Math.min(VISIBLE, items.size()); }
    private int listTop() { return getY() + getHeight(); }
    private int listHeight() { return rows() * ROW; }
    private int maxScroll() { return Math.max(0, items.size() - VISIBLE); }
    private boolean hasBar() { return items.size() > VISIBLE; }

    private boolean inList(double mx, double my) {
        return open && mx >= getX() && mx < getX() + getWidth()
                && my >= listTop() && my < listTop() + listHeight();
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return super.isMouseOver(mx, my) || inList(mx, my);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hover = super.isMouseOver(mx, my);
        g.blitSprite(hover ? BTN_HOVER : BTN, getX(), getY(), getWidth(), getHeight());
        String text = font.plainSubstrByWidth(labelOf.apply(items.get(selected)), getWidth() - 20);
        int ty = getY() + (getHeight() - 8) / 2;
        g.drawString(font, text, getX() + 6, ty, 0xFFFFFF);
        //g.drawString(font, open ? "^" : "v", getX() + getWidth() - 12, ty, 0xFFFFFF);
        g.drawString(font, open ? "▲" : "▼", getX() + getWidth() - 12, ty, 0xFFFFFF);
        if (!open) return;

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int x = getX(), y = listTop(), w = getWidth(), h = listHeight();
        boolean bar = hasBar();
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        g.fill(x, y, x + w, y + h, 0xFF2B2B2B);
        int textW = w - (bar ? 12 : 6);
        for (int i = 0; i < rows(); i++) {
            int idx = scroll + i;
            int ry = y + i * ROW;
            boolean over = mx >= x && mx < x + w - (bar ? 6 : 0) && my >= ry && my < ry + ROW;
            if (over) g.fill(x, ry, x + w - (bar ? 6 : 0), ry + ROW, 0xFF4A4A4A);
            String label = font.plainSubstrByWidth(labelOf.apply(items.get(idx)), textW);
            g.drawString(font, label, x + 4, ry + 3, idx == selected ? 0xFFFF55 : 0xFFFFFF);
        }
        if (bar) {
            int trackX = x + w - 5;
            g.fill(trackX, y, trackX + 4, y + h, 0xFF1A1A1A);
            int thumbH = Math.max(8, h * VISIBLE / items.size());
            int thumbY = y + (h - thumbH) * scroll / maxScroll();
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFF9A9A9A);
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible) return false;
        if (open) {
            if (button == 0 && inList(mx, my)) {
                if (hasBar() && mx >= getX() + getWidth() - 6) { // клик по полосе: прокрутка
                    double part = (my - listTop()) / listHeight();
                    scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(maxScroll() * part)));
                    return true;
                }
                int idx = scroll + (int) ((my - listTop()) / ROW);
                if (idx >= 0 && idx < items.size()) {
                    selected = idx;
                    onChange.accept(idx);
                }
            }
            open = false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (button == 0 && super.isMouseOver(mx, my)) {
            open = true;
            scroll = Math.min(Math.max(0, selected - VISIBLE / 2), maxScroll());
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!open || !(inList(mx, my) || super.isMouseOver(mx, my))) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}