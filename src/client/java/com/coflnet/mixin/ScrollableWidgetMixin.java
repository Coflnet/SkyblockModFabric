package com.coflnet.mixin;

import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ScrollableWidget.class)
public class ScrollableWidgetMixin {
    @ModifyArg(method = "drawScrollbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V"), index = 4)
    private int drawBox(int x){
        try {
            return MinecraftClient.getInstance().currentScreen instanceof BinGUI ? 2 : x;
        } catch (Exception e) {
            System.out.println("[ScrollableWidgetMixin] drawBox failed: " + e.getMessage());
            return x;
        }
    }
}
