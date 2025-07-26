package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    DescriptionHandler.DescModification[] openedWith = null;
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;
    @Shadow
    protected int backgroundHeight;
    private MultilineTextWidget sideTextWidget;

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        System.out.println("Injecting side text widget into HandledScreen");
    }

    private void updateText(DescriptionHandler.DescModification[] lines) {
        sideTextWidget = new MultilineTextWidget(
                15, 50,
                Text.of("a\nx\nfor init of widget long text"),
                MinecraftClient.getInstance().textRenderer
        );
        if(lines == null || lines.length == 0) {
            int count = lines == null ? 0 : lines.length;
            sideTextWidget.setMessage(Text.of("no lines\nmultiline " + count));
            return;
        }
        String temp = "";
        int maxWidth = 0;
        int lineCount = 0;
        for (DescriptionHandler.DescModification descModification : lines) {
            if(descModification.type.equals("APPEND")) {
                temp = temp + "\n" + descModification.value;
                int width = MinecraftClient.getInstance().textRenderer.getWidth(descModification.value);
                if(width > maxWidth) {
                    maxWidth = width;
                }
            }
            else if(descModification.type.equals("SUGGEST")) {
                temp += "\n§7Will suggest: §r" +descModification.value.split(": ")[1].trim();
            }
            else
                continue;
            lineCount++;
        }
        sideTextWidget.setMessage(Text.of(temp));
        sideTextWidget.setPosition(x - maxWidth - 5, y+5);
        sideTextWidget.setDimensions(maxWidth + 10, lineCount * 20);
        sideTextWidget.setAlpha(0.9f);
    }

    @Inject(at = @At("RETURN"), method = "render")
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        if(sideTextWidget != null)
            sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
        if(openedWith == null) {
            DescriptionHandler.DescModification[] currentText = CoflModClient.getExtraSlotDescMod();
            if(currentText.length == 0)
                return;
            updateText(currentText);
            System.out.println("Updating side text widget with new data");
            openedWith = currentText;
        }
    }

}
