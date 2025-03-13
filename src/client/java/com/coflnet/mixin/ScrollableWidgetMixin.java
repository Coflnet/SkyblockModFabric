package com.coflnet.mixin;

import com.coflnet.gui.cofl.CoflBinGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ScrollableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ScrollableWidget.class)
public class ScrollableWidgetMixin {
    @ModifyArg(method = "drawScrollbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"), index = 3)
    private int drawBox(int x){
        if(MinecraftClient.getInstance().currentScreen instanceof CoflBinGUI){
            return 2;
        } else {
            return x;
        }
    }
}
