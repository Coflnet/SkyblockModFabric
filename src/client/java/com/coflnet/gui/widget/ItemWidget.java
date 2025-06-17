package com.coflnet.gui.widget;

import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EmptyWidget;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ItemWidget extends EmptyWidget implements Element, Drawable, Selectable {
    public ItemStack item;

    public ItemWidget(int x, int y, ItemStack item) {
        super(x, y, 16, 16);
        this.item = item;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtils.drawItemStack(context, item, getX(), getY(), 1);

        if(isMouseOver(mouseX, mouseY)){
            context.drawItemTooltip(MinecraftClient.getInstance().textRenderer, item, mouseX, mouseY);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Element.super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return Element.super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return Element.super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return Element.super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return Element.super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return Element.super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return Element.super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return Element.super.charTyped(chr, modifiers);
    }

    @Override
    public @Nullable GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return Element.super.getNavigationPath(navigation);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= (double)this.getX()
                && mouseY >= (double)this.getY()
                && mouseX < (double)(this.getX() + getWidth())
                && mouseY < (double)(this.getY() + getHeight());
    }

    @Override
    public void setFocused(boolean focused) {

    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    public @Nullable GuiNavigationPath getFocusedPath() {
        return Element.super.getFocusedPath();
    }

    @Override
    public int getNavigationOrder() {
        return Element.super.getNavigationOrder();
    }

    @Override
    public ScreenRect getNavigationFocus() {
        return super.getNavigationFocus();
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
    }

    @Override
    public SelectionType getType() {
        return SelectionType.NONE;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {

    }
}
