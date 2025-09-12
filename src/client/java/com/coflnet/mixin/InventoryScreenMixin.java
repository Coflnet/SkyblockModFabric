package com.coflnet.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreenMixin{

    @Inject(at = @At("TAIL"), method = "render")
    public void renderMain(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci){
        try {
            super.renderMain(context, mouseX, mouseY, deltaTicks, ci);
        } catch (Exception e) {
            System.out.println("[InventoryScreenMixin] render TAIL failed: " + e.getMessage());
        }
    }
}
