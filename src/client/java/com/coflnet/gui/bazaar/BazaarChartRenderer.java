package com.coflnet.gui.bazaar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import com.coflnet.core.BazaarChart;

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
        draw(font, x, y, getWidth(font), getHeight(font), graphics, data,
                Component.translatable("tooltip.coflmod.chart.samples"));
    }

    public static void draw(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics,
                            BazaarChartTooltip data, Component footer) {
        draw(font, x, y, w, h, graphics, data, footer, 0);
    }

    public static void draw(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics,
                            BazaarChartTooltip data, Component footer, double maximumGap) {
        var bounds = bounds(x, y, w, h);
        int left = bounds.left(), right = bounds.right(), top = bounds.top(), bottom = bounds.bottom();
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
        if (maximumGap > 0) series(graphics, bounds, data.chart().points(), pointColor, maximumGap);
        else points(graphics, bounds, data.chart().points(), pointColor);
        graphics.text(font, footer,
                x + 3, y + h - 11, 0xFF99AABD, false);
    }

    public record Bounds(int left, int right, int top, int bottom) { }

    public static void series(GuiGraphicsExtractor graphics, Bounds bounds,
                              java.util.List<BazaarChart.Point> points, int color, double maximumGap) {
        int width = bounds.right() - bounds.left(), height = bounds.bottom() - bounds.top();
        for (int i = 1; i < points.size(); i++) {
            var from = points.get(i - 1);
            var to = points.get(i);
            if (!BazaarChart.connects(from, to, maximumGap)) continue;
            int x1 = bounds.left() + (int) Math.round(from.x() * width);
            int y1 = bounds.top() + (int) Math.round(from.y() * height);
            int x2 = bounds.left() + (int) Math.round(to.x() * width);
            int y2 = bounds.top() + (int) Math.round(to.y() * height);
            if (x1 == x2) {
                graphics.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
                continue;
            }
            int previousY = y1;
            for (int x = x1; x <= x2; x++) {
                int y = y1 + (int) Math.round((double) (x - x1) * (y2 - y1) / (x2 - x1));
                graphics.fill(x, Math.min(previousY, y), x + 1, Math.max(previousY, y) + 1, color);
                previousY = y;
            }
        }
        // Preserve isolated observations without density-dependent marker sizes.
        for (var point : points) {
            int x = bounds.left() + (int) Math.round(point.x() * width);
            int y = bounds.top() + (int) Math.round(point.y() * height);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    public static Bounds bounds(int x, int y, int width, int height) {
        return new Bounds(x + 42, x + width - 8, y + 17, y + height - 32);
    }

    public static void points(GuiGraphicsExtractor graphics, Bounds bounds,
                              java.util.List<com.coflnet.core.BazaarChart.Point> points, int color) {
        int left = bounds.left(), right = bounds.right(), top = bounds.top(), bottom = bounds.bottom();
        for (var point : points) {
            int px = left + (int) Math.round(point.x() * (right - left));
            int py = top + (int) Math.round(point.y() * (bottom - top));
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
    }

}
