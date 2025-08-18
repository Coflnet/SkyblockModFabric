package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
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
    @Shadow
    protected int x;
    @Shadow
    protected int y;
    @Shadow
    protected int backgroundWidth;
    @Shadow
    protected int backgroundHeight;
    protected MultilineTextWidget sideTextWidget;

    @Inject(at = @At("TAIL"), method = "init")
    public void init(CallbackInfo ci){
        // init to whatever text is present
        updateText(CoflModClient.getExtraSlotDescMod());
        String currentTitle = ((HandledScreen<?>) (Object) this).getTitle().getString();
        if(sideTextWidget != null && !currentTitle.equals("Crafting")) {
            sideTextWidget.setAlpha(0.3f); // make it transparent until properly loaded
        }
        DescriptionHandler.setRefreshCallback((lines, title) -> {

            System.out.println("Updating info for " + title + " with " + lines.length + " lines");
            updateText(CoflModClient.getExtraSlotDescMod());
        });
    }

    protected void updateText(DescriptionHandler.DescModification[] lines) {

        if(lines == null || lines.length == 0) {
            int count = lines == null ? 0 : lines.length;
            sideTextWidget = null;
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
        sideTextWidget = new MultilineTextWidget(
                x - maxWidth - 5, y+5,
                Text.of(temp),
                MinecraftClient.getInstance().textRenderer
        );
        sideTextWidget.setDimensions(maxWidth + 10, lineCount * 20);
        sideTextWidget.setAlpha(0.9f);
    }

    @Inject(at = @At("HEAD"), method = "render")
    public void renderMain(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        if(sideTextWidget != null)
            sideTextWidget.render(context, mouseX, mouseY, deltaTicks);
    }
}
