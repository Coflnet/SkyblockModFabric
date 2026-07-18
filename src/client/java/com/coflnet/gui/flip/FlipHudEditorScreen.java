package com.coflnet.gui.flip;

import com.coflnet.config.FlipHudManager;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class FlipHudEditorScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private int hudX;
    private int hudY;

    public FlipHudEditorScreen(Screen parent) {
        super(Component.literal("flip hud position"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        hudX = FlipHudManager.getX(width, FlipHud.WIDTH);
        hudY = FlipHudManager.getY(height, FlipHud.HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x99000000);
        RenderUtils.drawCenteredString(context, "drag the flip hud, then press escape to save",
                width / 2, Math.max(10, height / 2 - 70), 0xFFFFFFFF);
        FlipHud.renderAt(context, hudX, hudY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (inside(click.x(), click.y())) {
            dragging = true;
            dragOffsetX = (int) click.x() - hudX;
            dragOffsetY = (int) click.y() - hudY;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
        if (!dragging) {
            return super.mouseDragged(click, dragX, dragY);
        }
        hudX = clamp((int) click.x() - dragOffsetX, 2, Math.max(2, width - FlipHud.WIDTH - 2));
        hudY = clamp((int) click.y() - dragOffsetY, 2, Math.max(2, height - FlipHud.HEIGHT - 2));
        FlipHudManager.setPosition(hudX, hudY, false);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragging) {
            dragging = false;
            FlipHudManager.setPosition(hudX, hudY, true);
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void onClose() {
        FlipHudManager.setPosition(hudX, hudY, true);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private boolean inside(double x, double y) {
        return x >= hudX && x < hudX + FlipHud.WIDTH && y >= hudY && y < hudY + FlipHud.HEIGHT;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
