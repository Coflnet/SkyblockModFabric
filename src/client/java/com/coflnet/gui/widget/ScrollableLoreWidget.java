package com.coflnet.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A scrollable widget that renders multi-line lore text with clipping.
 * Only consumes clicks on the scrollbar; other clicks are forwarded to the
 * click-through target so the "click anywhere" buy widget can handle them.
 * The screen routes clicks and wheel scrolls to the same child, so the text
 * area has to stay hoverable for scrolling and forward clicks itself.
 */
public class ScrollableLoreWidget extends AbstractScrollArea {
    private final Font font;
    private Component message;
    private List<FormattedCharSequence> lines = List.of();
    private final int textColor;
    private GuiEventListener clickThrough;

    public ScrollableLoreWidget(int x, int y, int width, int height, Component message, int textColor) {
        super(x, y, width, height, Component.empty(), defaultSettings(height));
        this.font = Minecraft.getInstance().font;
        this.textColor = textColor;
        setMessage(message);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.message = message;
        this.lines = font.split(message, getWidth() - scrollbarWidth() - 4);
        refreshScrollAmount();
    }

    @Override
    protected int contentHeight() {
        return lines.size() * (font.lineHeight + 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean fromScreen) {
        if (updateScrolling(click)) {
            return true;
        }
        return clickThrough != null && clickThrough.mouseClicked(click, fromScreen);
    }

    /** Sets the widget that receives clicks on the text, i.e. outside the scrollbar. */
    public void setClickThrough(GuiEventListener clickThrough) {
        this.clickThrough = clickThrough;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.enableScissor(getX(), getY(), getRight(), getBottom());

        int yOffset = getY() - (int) scrollAmount();
        for (FormattedCharSequence line : lines) {
            if (yOffset + font.lineHeight > getY() - font.lineHeight && yOffset < getBottom() + font.lineHeight) {
                context.text(font, line, getX() + 2, yOffset, textColor);
            }
            yOffset += font.lineHeight + 1;
        }

        context.disableScissor();

        extractScrollbar(context, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
