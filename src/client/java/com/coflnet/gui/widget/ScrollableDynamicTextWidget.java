package com.coflnet.gui.widget;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;

import java.util.Objects;

public class ScrollableDynamicTextWidget extends ScrollableWidget {
    private final TextRenderer textRenderer;
    public MultilineTextWidget wrapped;

    public ScrollableDynamicTextWidget(int x, int y, int width, int height, Text message, TextRenderer textRenderer) {
        super(x, y, width, height, message);
        this.textRenderer = textRenderer;
        this.wrapped = (new MultilineTextWidget(message, textRenderer)).setMaxWidth(this.getWidth() - this.getPaddingDoubled());
    }

    public void updateText(Text message) {
        this.wrapped = (new MultilineTextWidget(message, textRenderer)).setMaxWidth(this.getWidth() - this.getPaddingDoubled());
    }

    public ScrollableDynamicTextWidget textColor(int textColor) {
        this.wrapped.setTextColor(textColor);
        return this;
    }

    public void setWidth(int width) {
        super.setWidth(width);
        this.wrapped.setMaxWidth(this.getWidth() - this.getPaddingDoubled());
    }

    protected int getContentsHeight() {
        return this.wrapped.getHeight();
    }

    protected double getDeltaYPerScroll() {
        Objects.requireNonNull(this.textRenderer);
        return (double)9.0F;
    }

    protected void drawBox(DrawContext context) {
        if (this.overflows()) {
            super.drawBox(context);
        } else if (this.isFocused()) {
            this.drawBox(context, this.getX() - this.getPadding(), this.getY() - this.getPadding(), this.getWidth() + this.getPaddingDoubled(), this.getHeight() + this.getPaddingDoubled());
        }

    }

    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.visible) {
            if (!this.overflows()) {
                this.drawBox(context);
                context.getMatrices().push();
                context.getMatrices().translate((float)this.getX(), (float)this.getY(), 0.0F);
                this.wrapped.render(context, mouseX, mouseY, delta);
                context.getMatrices().pop();
            } else {
                super.renderWidget(context, mouseX, mouseY, delta);
            }

        }
    }

    public boolean textOverflows() {
        return super.overflows();
    }

    protected void renderContents(DrawContext context, int mouseX, int mouseY, float delta) {
        context.getMatrices().push();
        context.getMatrices().translate((float)(this.getX() + this.getPadding()), (float)(this.getY() + this.getPadding()), 0.0F);
        this.wrapped.render(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
    }

    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, this.getMessage());
    }
}
