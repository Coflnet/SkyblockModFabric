package com.coflnet.gui.hud;

import com.coflnet.config.CoflModConfig;
import com.coflnet.core.InfoDisplayLayout;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

/**
 * Editor for the 3 permanent HUD info displays, opened by {@code /cofl displays}
 * or the "Edit display layout" settings button. Draws all three (with a
 * placeholder for any that have no content yet) so they can be positioned
 * before any data arrives.
 * <p>
 * Controls: left-click selects, left-drag moves; mouse wheel rescales the
 * display under the cursor, Shift+wheel adjusts background transparency,
 * Ctrl+wheel adjusts text transparency; arrow keys nudge the selected display
 * by 1px (Shift: 10px); H toggles it enabled, R resets it to defaults, Escape
 * closes. All positions are stored as fractions of the screen, so nothing
 * needs to be recomputed on resize.
 */
public class InfoDisplayEditScreen extends Screen {
    private static final int OUTLINE_SELECTED = 0xFFFFD54A;
    private static final int OUTLINE_NORMAL = 0xFFAAAAAA;
    private static final int OUTLINE_DISABLED = 0xFF555555;
    private static final int DISABLED_BG_ALPHA = 0x26000000; // ~15% black
    private static final String HELP_TEXT =
            "§7Click: select  Drag: move  Scroll: scale  Shift+Scroll: bg alpha  "
                    + "Ctrl+Scroll: text alpha  Arrows: nudge (Shift x10)  H: toggle  R: reset  Esc: close";

    private final Screen parent;
    private int selectedId = 1;

    private boolean dragging = false;
    private double dragGrabDX;
    private double dragGrabDY;

    private Button doneButton;

    public InfoDisplayEditScreen(Screen parent) {
        super(Component.literal("SkyCofl Info Displays"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 80;
        int h = 20;
        doneButton = addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(this.width - w - 8, this.height - h - 24, w, h)
                .build());
    }

    /**
     * {@link Screen#resize(int, int)} only updates width/height and calls this - it does NOT call
     * {@link #init()} again - so the Done button's position must be refreshed here or it stays anchored to
     * the old window size. The display boxes themselves need no such fix-up: their rects are recomputed
     * every frame from their stored fractions plus the current width/height (see {@link #rectFor}).
     */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (doneButton != null) {
            doneButton.setX(this.width - doneButton.getWidth() - 8);
            doneButton.setY(this.height - doneButton.getHeight() - 24);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        CoflModConfig config = CoflModConfig.get();
        for (int id = 1; id <= InfoDisplayManager.DISPLAY_COUNT; id++) {
            CoflModConfig.InfoDisplaySettings settings = config.displaySettings(id);
            if (settings == null) {
                continue;
            }
            drawBox(context, settings, InfoDisplayManager.snapshot(id), id == selectedId);
        }
        RenderUtils.drawStringWithShadow(context, HELP_TEXT, 8, this.height - 12, CoflColConfig.TEXT_PRIMARY);
    }

    private void drawBox(GuiGraphicsExtractor context, CoflModConfig.InfoDisplaySettings settings,
                          InfoDisplayManager.Snapshot snapshot, boolean selected) {
        int[] rect = rectFor(settings, snapshot);
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];

        int bgColor;
        if (settings.enabled) {
            int bgAlpha = (int) Math.round(InfoDisplayLayout.clampBackgroundAlpha(settings.backgroundAlpha) * 255.0);
            bgColor = bgAlpha << 24;
        } else {
            bgColor = DISABLED_BG_ALPHA;
        }
        RenderUtils.drawRect(context, x, y, w, h, bgColor);

        int outline = selected ? OUTLINE_SELECTED : (settings.enabled ? OUTLINE_NORMAL : OUTLINE_DISABLED);
        context.fill(x, y, x + w, y + 1, outline);
        context.fill(x, y + h - 1, x + w, y + h, outline);
        context.fill(x, y, x + 1, y + h, outline);
        context.fill(x + w - 1, y, x + w, y + h, outline);

        int textAlpha = (int) Math.round(InfoDisplayLayout.clampTextAlpha(settings.enabled ? settings.textAlpha : 0.3) * 255.0);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.scale((float) settings.scale);

        int pad = InfoDisplayRenderer.PADDING;
        int cursorY = pad;
        if (snapshot != null) {
            if (snapshot.title != null) {
                context.text(RenderUtils.textRenderer, snapshot.title, pad, cursorY, textColor, true);
                cursorY += snapshot.lineHeight + 2;
            }
            for (Component line : snapshot.lines) {
                context.text(RenderUtils.textRenderer, line, pad, cursorY, textColor, true);
                cursorY += snapshot.lineHeight;
            }
        } else {
            context.text(RenderUtils.textRenderer, "§7Display " + settings.id + " - waiting for data", pad, cursorY, textColor, true);
        }
        pose.popMatrix();

        String label = "#" + settings.id
                + "  " + Math.round(settings.scale * 100) + "%"
                + "  bg " + Math.round(settings.backgroundAlpha * 100) + "%"
                + "  text " + Math.round(settings.textAlpha * 100) + "%"
                + (settings.enabled ? "" : "  (disabled)");
        RenderUtils.drawStringWithShadow(context, label, x, Math.max(0, y - 10), CoflColConfig.TEXT_PRIMARY);
    }

    /** Current on-screen rect {x, y, width, height} for a display, clamped fully on screen. */
    private int[] rectFor(CoflModConfig.InfoDisplaySettings settings, InfoDisplayManager.Snapshot snapshot) {
        int[] size = InfoDisplayRenderer.contentSize(snapshot);
        int scaledW = (int) Math.round(size[0] * settings.scale);
        int scaledH = (int) Math.round(size[1] * settings.scale);
        double[] clamped = InfoDisplayLayout.clampToScreen(settings.x, settings.y, scaledW, scaledH, this.width, this.height);
        int x = InfoDisplayLayout.toPixelX(clamped[0], this.width);
        int y = InfoDisplayLayout.toPixelY(clamped[1], this.height);
        return new int[]{x, y, scaledW, scaledH};
    }

    private int[] rectFor(int id) {
        CoflModConfig.InfoDisplaySettings settings = CoflModConfig.get().displaySettings(id);
        if (settings == null) {
            return null;
        }
        return rectFor(settings, InfoDisplayManager.snapshot(id));
    }

    private static boolean inRect(double px, double py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            // Later ids are drawn on top, so hit-test from the topmost down.
            for (int id = InfoDisplayManager.DISPLAY_COUNT; id >= 1; id--) {
                int[] r = rectFor(id);
                if (r != null && inRect(mx, my, r[0], r[1], r[2], r[3])) {
                    selectedId = id;
                    dragging = true;
                    dragGrabDX = mx - r[0];
                    dragGrabDY = my - r[1];
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (dragging && click.button() == 0) {
            CoflModConfig.InfoDisplaySettings settings = CoflModConfig.get().displaySettings(selectedId);
            if (settings != null) {
                int[] size = InfoDisplayRenderer.contentSize(InfoDisplayManager.snapshot(selectedId));
                int scaledW = (int) Math.round(size[0] * settings.scale);
                int scaledH = (int) Math.round(size[1] * settings.scale);
                int newX = (int) Math.round(click.x() - dragGrabDX);
                int newY = (int) Math.round(click.y() - dragGrabDY);
                double[] clamped = InfoDisplayLayout.clampToScreen(
                        InfoDisplayLayout.toFractionX(newX, this.width),
                        InfoDisplayLayout.toFractionY(newY, this.height),
                        scaledW, scaledH, this.width, this.height);
                settings.x = clamped[0];
                settings.y = clamped[1];
            }
            return true;
        }
        return super.mouseDragged(click, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging && click.button() == 0) {
            dragging = false;
            save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Integer overId = null;
        for (int id = 1; id <= InfoDisplayManager.DISPLAY_COUNT; id++) {
            int[] r = rectFor(id);
            if (r != null && inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                overId = id;
            }
        }
        if (overId == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        double dir = Math.signum(scrollY);
        if (dir == 0) {
            return true;
        }

        selectedId = overId;
        CoflModConfig.InfoDisplaySettings settings = CoflModConfig.get().displaySettings(overId);
        if (settings == null) {
            return true;
        }

        var window = Minecraft.getInstance().getWindow();
        boolean shift = InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
        boolean ctrl = InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);

        if (shift) {
            settings.backgroundAlpha = InfoDisplayLayout.clampBackgroundAlpha(settings.backgroundAlpha + dir * 0.05);
        } else if (ctrl) {
            settings.textAlpha = InfoDisplayLayout.clampTextAlpha(settings.textAlpha + dir * 0.05);
        } else {
            settings.scale = InfoDisplayLayout.clampScale(settings.scale + dir * 0.1);
        }
        save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        CoflModConfig.InfoDisplaySettings settings = CoflModConfig.get().displaySettings(selectedId);
        if (settings != null) {
            int step = event.hasShiftDown() ? 10 : 1;
            int key = event.key();
            if (key == GLFW.GLFW_KEY_UP) {
                nudge(settings, 0, -step);
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                nudge(settings, 0, step);
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                nudge(settings, -step, 0);
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                nudge(settings, step, 0);
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_H) {
                settings.enabled = !settings.enabled;
                save();
                return true;
            }
            if (key == GLFW.GLFW_KEY_R) {
                resetToDefault(settings);
                save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void nudge(CoflModConfig.InfoDisplaySettings settings, int dxPx, int dyPx) {
        int[] size = InfoDisplayRenderer.contentSize(InfoDisplayManager.snapshot(settings.id));
        int scaledW = (int) Math.round(size[0] * settings.scale);
        int scaledH = (int) Math.round(size[1] * settings.scale);
        int curX = InfoDisplayLayout.toPixelX(settings.x, this.width);
        int curY = InfoDisplayLayout.toPixelY(settings.y, this.height);
        double[] clamped = InfoDisplayLayout.clampToScreen(
                InfoDisplayLayout.toFractionX(curX + dxPx, this.width),
                InfoDisplayLayout.toFractionY(curY + dyPx, this.height),
                scaledW, scaledH, this.width, this.height);
        settings.x = clamped[0];
        settings.y = clamped[1];
    }

    private void resetToDefault(CoflModConfig.InfoDisplaySettings settings) {
        CoflModConfig.InfoDisplaySettings fresh = CoflModConfig.defaultInfoDisplay(settings.id);
        settings.enabled = fresh.enabled;
        settings.x = fresh.x;
        settings.y = fresh.y;
        settings.scale = fresh.scale;
        settings.backgroundAlpha = fresh.backgroundAlpha;
        settings.textAlpha = fresh.textAlpha;
    }

    private void save() {
        CoflModConfig.get().save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        save();
        Minecraft.getInstance().setScreen(parent);
    }
}
