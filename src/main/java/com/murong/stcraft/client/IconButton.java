package com.murong.stcraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Квадратная кнопка с рисованной иконкой и подсказкой при наведении. */
public class IconButton extends Button
{

    @FunctionalInterface
    public interface Icon
    {
        void paint(GuiGraphics g, int x, int y, int size, boolean active);
    }

    /** Закрашенный треугольник ▲ */
    public static final Icon ARROW_UP = (g, x, y, size, active) ->
    {
        int color = active ? 0xFFFFFFFF : 0xFF777777;
        int cx = x + size / 2;
        int top = y + (size - 6) / 2;
        for (int r = 0; r < 6; r++)
        {
            g.fill(cx - r, top + r, cx + r + 1, top + r + 1, color);
        }
    };

    /** Спрайт из набора интерфейса игры (15×15, по центру кнопки). */
    public static Icon sprite(ResourceLocation id)
    {
        return (g, x, y, size, active) ->
                g.blitSprite(id, x + (size - 15) / 2, y + (size - 15) / 2, 15, 15);
    }

    private final Icon icon;

    public IconButton(int x, int y, int size, Component tooltip, OnPress onPress, Icon icon)
    {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        super.renderWidget(g, mx, my, pt);
        icon.paint(g, getX(), getY(), getWidth(), active);
    }
}