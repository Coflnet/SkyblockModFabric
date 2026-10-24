package com.coflnet.gui.bazaar;

import com.coflnet.core.BazaarGraphTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.coflnet.core.BazaarHistory;
import com.coflnet.core.BazaarHistoryClient;
import com.coflnet.core.BazaarHistoryRecovery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/** Local graphs backed by the same history as Cofl's website. No remote Bazaar menu is needed. */
public final class BazaarHistoryScreen extends Screen {
    private final AbstractContainerScreen<?> parent;
    private final BazaarGraphTarget target;
    private final ZoneId zone = ZoneId.systemDefault();
    private final DateTimeFormatter preciseTime = DateTimeFormatter.ofPattern("MMM d HH:mm:ss z").withZone(zone);
    private BazaarHistoryClient.Range range = BazaarHistoryClient.Range.DAY;
    private BazaarHistory.Metric metric = BazaarHistory.Metric.BUY_PRICE;
    private BazaarHistory history;
    private BazaarHistory.Plot plot;
    private Component status = message("loading");
    private int revision, zoom = 1;
    private boolean started, compare, refreshFailed;
    private Button retryButton;

    public BazaarHistoryScreen(AbstractContainerScreen<?> parent, BazaarGraphTarget target) {
        super(Component.literal(target.query()));
        this.parent = parent;
        this.target = target;
    }

    private static Component message(String key) { return Component.translatable("screen.coflmod.history." + key); }
    private static Component metricName(BazaarHistory.Metric metric) { return message(metric.name().toLowerCase(java.util.Locale.ROOT)); }

    @Override
    protected void init() {
        int x = 16;
        for (var span : BazaarHistoryClient.Range.values()) {
            var button = addRenderableWidget(Button.builder(message(span.name().toLowerCase(java.util.Locale.ROOT)), selected -> {
                range = span; zoom = 1; load(); repositionElements();
            }).bounds(x, 31, 32, 20).build());
            button.active = span != range;
            x += 36;
        }
        addRenderableWidget(Button.builder(message(compare ? "compare_on" : "compare"), button -> {
            compare = !compare; button.setMessage(message(compare ? "compare_on" : "compare")); updatePlot();
        }).bounds(width - 146, 31, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zoom ×" + zoom), button -> changeZoom(zoom == 8 ? 1 : zoom * 2))
                .bounds(width - 72, 31, 56, 20).tooltip(Tooltip.create(message("zoom_help"))).build());
        int columnWidth = (width - 40) / 3;
        for (var choice : BazaarHistory.Metric.values()) {
            var button = addRenderableWidget(Button.builder(metricName(choice), selected -> {
                metric = choice; updatePlot(); repositionElements();
            }).bounds(16 + choice.column() * (columnWidth + 4), 55 + choice.row() * 23, columnWidth, 20).build());
            button.active = choice != metric;
            if (choice.moving()) button.setTooltip(Tooltip.create(message("moving_help")));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width - 76, height - 27, 60, 20).build());
        retryButton = addRenderableWidget(Button.builder(message("retry"), button -> load())
                .bounds(width - 142, height - 27, 60, 20).build());
        retryButton.visible = refreshFailed;
        if (!started) { started = true; load(); } else updatePlot();
    }

    @Override
    protected void repositionElements() { clearWidgets(); init(); }

    private void changeZoom(int value) {
        zoom = value;
        updatePlot();
        repositionElements();
    }

    private void updatePlot() {
        if (history == null || history.samples().isEmpty()) { plot = null; return; }
        Instant end = history.requestedEnd() == null ? Instant.now() : history.requestedEnd();
        long milliseconds = range.duration.toMillis() / zoom;
        int tickWidth = milliseconds > 86_400_000 ? 85 : 50;
        int ticks = Math.clamp((width - 82) / tickWidth + 1, 2, 7);
        plot = history.plot(metric, end.minusMillis(milliseconds), end, ticks, compare, zone);
    }

    private void load() {
        int request = ++revision;
        history = BazaarHistoryClient.cached(target, range);
        updatePlot();
        status = message("loading");
        refreshFailed = false;
        if (retryButton != null) retryButton.visible = false;
        request(request, range, false);
    }

    private void request(int request, BazaarHistoryClient.Range requestedRange, boolean retry) {
        BazaarHistoryClient.load(target, requestedRange).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (request != revision || Minecraft.getInstance().gui.screen() != this) return;
            if (error != null) {
                if (!retry && BazaarHistoryClient.stale(target, requestedRange) && BazaarHistoryClient.retryable(error)) {
                    java.util.concurrent.CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS).execute(() ->
                            Minecraft.getInstance().execute(() -> {
                                if (request == revision && Minecraft.getInstance().gui.screen() == this)
                                    request(request, requestedRange, true);
                            }));
                    return;
                }
                status = message("error");
                refreshFailed = BazaarHistoryClient.refreshFailed(target, requestedRange);
                retryButton.visible = refreshFailed;
                return;
            }
            history = result;
            updatePlot();
            status = message("empty");
        }));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF0101724);
        graphics.text(font, font.plainSubstrByWidth(target.query(), width - 32), 16, 14, 0xFFFFFFFF, false);
        if (plot == null) {
            graphics.text(font, status, 16, 115, 0xFFCDD6E5, false);
            return;
        }
        int x = 16, y = 103, w = width - 32, h = height - 137;
        var source = Component.empty().append(message(metric.moving() ? "moving_source" : "source"));
        Instant requestedEnd = history.requestedEnd() == null ? Instant.now() : history.requestedEnd();
        if (BazaarHistoryRecovery.incomplete(history, range, requestedEnd))
            source.append(" | ").append(message("incomplete").copy().withColor(0xFFCC66));
        double maximumGap = (double) BazaarHistoryRecovery.gapTolerance(range).toMillis() / (plot.to() - plot.from());
        BazaarChartRenderer.draw(font, x, y, w, h, graphics,
                new BazaarChartTooltip(plot.chart(), metric.buy(), metric.volume()),
                source, maximumGap);
        var bounds = BazaarChartRenderer.bounds(x, y, w, h);
        if (compare) BazaarChartRenderer.series(graphics, bounds, plot.comparison(), metric.buy() ? 0xFFD99CFF : 0xFF54DCFF, maximumGap);
        if (compare) {
            graphics.text(font, message("legend_buy"), width - 92, y + 3, 0xFF54DCFF, false);
            graphics.text(font, message("legend_sell"), width - 52, y + 3, 0xFFD99CFF, false);
        }
        if (mouseX >= bounds.left() && mouseX <= bounds.right() && mouseY >= bounds.top() && mouseY <= bounds.bottom()) {
            double fraction = (double) (mouseX - bounds.left()) / (bounds.right() - bounds.left());
            var sample = plot.nearest(fraction);
            int at = bounds.left() + (int) Math.round((double) (sample.time().toEpochMilli() - plot.from()) / (plot.to() - plot.from()) * (bounds.right() - bounds.left()));
            graphics.verticalLine(at, bounds.top(), bounds.bottom(), 0xFF99AABD);
            var lines = new ArrayList<Component>();
            lines.add(Component.literal(preciseTime.format(sample.time())));
            lines.add(valueLine(sample, metric));
            if (compare) lines.add(valueLine(sample, metric.counterpart()));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
        var start = Instant.ofEpochMilli(plot.from()).atZone(zone);
        var end = Instant.ofEpochMilli(plot.to()).atZone(zone);
        var date = DateTimeFormatter.ofPattern("MMM d HH:mm");
        String period = date.format(start) + " – " + date.format(end) + " " + end.getZone().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault());
        if (refreshFailed)
            graphics.text(font, font.plainSubstrByWidth(message("stale").getString(), width - 166), 16, height - 21, 0xFFFFCC66, false);
        else graphics.text(font, font.plainSubstrByWidth(period, width - 100), 16, height - 21, 0xFF99AABD, false);
    }

    private Component valueLine(BazaarHistory.Sample sample, BazaarHistory.Metric choice) {
        var number = java.text.NumberFormat.getNumberInstance();
        number.setMaximumFractionDigits(choice.volume() ? 0 : 2);
        double value = BazaarHistory.value(sample, choice);
        return Component.literal(metricName(choice).getString() + ": " + (Double.isFinite(value) ? number.format(value) : "—"))
                .withColor(choice.buy() ? 0x54DCFF : 0xD99CFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && event.y() >= 103 && event.y() < height - 34) {
            changeZoom(zoom == 8 ? 1 : zoom * 2); return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        BazaarCharts.releaseMouse(event);
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (y >= 103 && y < height - 34 && vertical != 0) {
            changeZoom(Math.clamp(vertical > 0 ? zoom * 2 : zoom / 2, 1, 8)); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        revision++;
        var client = Minecraft.getInstance();
        client.gui.setScreen(client.player != null && client.player.containerMenu == parent.getMenu() ? parent : null);
    }
}
