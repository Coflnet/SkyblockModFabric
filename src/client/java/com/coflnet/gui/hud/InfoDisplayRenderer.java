package com.coflnet.gui.hud;

import com.coflnet.config.CoflModConfig;
import com.coflnet.core.InfoDisplayLayout;
import com.coflnet.gui.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import org.joml.Matrix3x2fStack;

/**
 * Renders the permanent, backend-updatable HUD info displays. Registered via
 * {@code HudElementRegistry.addLast} in {@code CoflModClient.onInitializeClient}.
 * <p>
 * Draws a translucent background rect, an optional title, then each line,
 * scaled by the display's user-configured scale. All content (Components,
 * widths, height) is precomputed once per update by {@link InfoDisplayManager}
 * - the per-frame path here is just: check expiry, read the snapshot, draw.
 */
public final class InfoDisplayRenderer implements HudElement {
    public static final InfoDisplayRenderer INSTANCE = new InfoDisplayRenderer();

    /** Padding (px, unscaled) around the text inside a display's background. */
    public static final int PADDING = 4;
    /** Placeholder box size (px, unscaled) used by the editor for an empty display. */
    public static final int PLACEHOLDER_WIDTH = 170;
    public static final int PLACEHOLDER_HEIGHT = 24;

    private InfoDisplayRenderer() {
    }

    /** Finds a line using the same scaled, clamped geometry as rendering, topmost panel first. */
    public static Style styleAt(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.hud.isHidden() || !isVisible(client.gui.screen())) {
            return null;
        }
        CoflModConfig config = CoflModConfig.get();
        for (int id = InfoDisplayManager.DISPLAY_COUNT; id >= 1; id--) {
            var settings = config.displaySettings(id);
            var snapshot = InfoDisplayManager.snapshot(id);
            if (settings == null || !settings.enabled || snapshot == null) {
                continue;
            }
            float scale = (float) settings.scale;
            int width = snapshot.maxWidth + PADDING * 2;
            int height = snapshot.contentHeight + PADDING * 2;
            int x = InfoDisplayLayout.clampX(settings.x, Math.round(width * scale), screenWidth);
            int y = InfoDisplayLayout.clampY(settings.y, Math.round(height * scale), screenHeight);
            double localX = (mouseX - x) / scale;
            double localY = (mouseY - y) / scale;
            if (localX < 0 || localX >= width || localY < 0 || localY >= height) {
                continue;
            }
            double lineY = localY - PADDING - (snapshot.title != null ? snapshot.lineHeight + 2 : 0);
            int index = (int) Math.floor(lineY / snapshot.lineHeight);
            if (index >= 0 && index < snapshot.lines.size()) {
                Component line = snapshot.lines.get(index);
                if (localX >= PADDING && localX < PADDING + client.font.width(line)) {
                    return line.getStyle();
                }
            }
            return null;
        }
        return null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!isVisible(screen)) {
            return;
        }
        CoflModConfig config = CoflModConfig.get();

        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();

        for (int id = 1; id <= InfoDisplayManager.DISPLAY_COUNT; id++) {
            CoflModConfig.InfoDisplaySettings settings = config.displaySettings(id);
            if (settings == null || !settings.enabled) {
                continue;
            }
            InfoDisplayManager.Snapshot snapshot = InfoDisplayManager.snapshot(id);
            if (snapshot == null) {
                continue;
            }
            render(context, screenWidth, screenHeight, settings, snapshot);
        }
    }

    private static boolean isVisible(Screen screen) {
        return !(screen instanceof InfoDisplayEditScreen)
                && (screen == null || screen instanceof ChatScreen || CoflModConfig.get().infoDisplaysShowInGuis);
    }

    /** Run after the screen's tooltips are queued, before Minecraft extracts them. */
    public static void renderHover(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Style style = styleAt(mouseX, mouseY, context.guiWidth(), context.guiHeight());
        if (style != null && style.getHoverEvent() instanceof HoverEvent.ShowText hover) {
            var font = Minecraft.getInstance().font;
            context.setTooltipForNextFrame(font,
                    font.split(hover.value(), Math.max(1, Math.min(300, context.guiWidth() - 16))),
                    DefaultTooltipPositioner.INSTANCE, mouseX, mouseY, true);
        }
    }

    /** Unscaled content box size (background + text) for a display; a fixed placeholder size when empty. */
    public static int[] contentSize(InfoDisplayManager.Snapshot snapshot) {
        if (snapshot == null) {
            return new int[]{PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT};
        }
        return new int[]{snapshot.maxWidth + PADDING * 2, snapshot.contentHeight + PADDING * 2};
    }

    /**
     * Draws one display. Deliberately does not call {@link #contentSize} / {@link InfoDisplayLayout#clampToScreen}
     * (each allocates a small array) - everything here is computed inline from primitives/the snapshot's
     * already-precomputed fields, so this runs with zero allocations per display per frame.
     */
    private static void render(GuiGraphicsExtractor context, int screenWidth, int screenHeight,
                                CoflModConfig.InfoDisplaySettings settings, InfoDisplayManager.Snapshot snapshot) {
        int contentWidth = snapshot.maxWidth + PADDING * 2;
        int contentHeight = snapshot.contentHeight + PADDING * 2;
        float scale = (float) settings.scale;
        int scaledWidth = Math.round(contentWidth * scale);
        int scaledHeight = Math.round(contentHeight * scale);

        int x = InfoDisplayLayout.clampX(settings.x, scaledWidth, screenWidth);
        int y = InfoDisplayLayout.clampY(settings.y, scaledHeight, screenHeight);

        int bgAlpha = (int) Math.round(InfoDisplayLayout.clampBackgroundAlpha(settings.backgroundAlpha) * 255.0);
        int bgColor = bgAlpha << 24;
        int textAlpha = (int) Math.round(InfoDisplayLayout.clampTextAlpha(settings.textAlpha) * 255.0);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale(scale);

        RenderUtils.drawRect(context, 0, 0, contentWidth, contentHeight, bgColor);

        int cursorY = PADDING;
        if (snapshot.title != null) {
            context.text(RenderUtils.textRenderer, snapshot.title, PADDING, cursorY, textColor, true);
            cursorY += snapshot.lineHeight + 2;
        }
        for (Component line : snapshot.lines) {
            context.text(RenderUtils.textRenderer, line, PADDING, cursorY, textColor, true);
            cursorY += snapshot.lineHeight;
        }

        pose.popMatrix();
    }
}
