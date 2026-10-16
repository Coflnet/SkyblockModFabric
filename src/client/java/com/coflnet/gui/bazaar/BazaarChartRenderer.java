package com.coflnet.gui.bazaar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

public final class BazaarChartRenderer implements ClientTooltipComponent {
    private final BazaarChartTooltip data;

    public BazaarChartRenderer(BazaarChartTooltip data) {
        this.data = data;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(140, Math.min(330, Minecraft.getInstance().getWindow().getGuiScaledWidth() - 24));
    }

    @Override
    public int getHeight(Font font) {
        return Math.max(72, Math.min(146, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 88));
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        int w = getWidth(font), h = getHeight(font);
        int left = x + 42, right = x + w - 8, top = y + 17, bottom = y + h - 32;
        int pointColor = data.buy() ? 0xFF54DCFF : 0xFFD99CFF;
        graphics.fill(x, y, x + w, y + h, 0xF0101724);
        graphics.text(font, Component.translatable(data.volume()
                ? "tooltip.coflmod.chart.quantity" : "tooltip.coflmod.chart.coins"), x + 3, y + 3, 0xFFCDD6E5, false);

        int previous = Integer.MIN_VALUE / 2;
        for (var label : data.chart().labels()) {
            int at = top + (int) Math.round(label.y() * (bottom - top));
            graphics.horizontalLine(left, right, at, 0xFF344356);
            if (at - previous < font.lineHeight + 2 && label.y() < 1) continue;
            graphics.text(font, label.text(), left - 5 - font.width(label.text()),
                    at - font.lineHeight / 2, 0xFFD8DFEA, false);
            previous = at;
        }
        int count = data.chart().times().size();
        for (int i = 0; i < count; i++) {
            int at = left + (int) Math.round((double) i / (count - 1) * (right - left));
            graphics.verticalLine(at, top, bottom, 0xFF283648);
            String time = data.chart().times().get(i);
            int tx = Math.max(x, Math.min(x + w - font.width(time), at - font.width(time) / 2));
            graphics.text(font, time, tx, bottom + 6, 0xFFD8DFEA, false);
        }
        graphics.verticalLine(left, top, bottom, 0xFF9AAABE);
        graphics.horizontalLine(left, right, bottom, 0xFF9AAABE);
        for (var point : data.chart().points()) {
            int px = left + (int) Math.round(point.x() * (right - left));
            int py = top + (int) Math.round(point.y() * (bottom - top));
            graphics.fill(px - 1, py - 1, px + 2, py + 2, pointColor);
        }
        graphics.text(font, Component.translatable("tooltip.coflmod.chart.samples"),
                x + 3, y + h - 11, 0xFF99AABD, false);
    }
}
