package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(HandledScreen.class) // Target HandledScreen, which is the base for most container UIs
public abstract class ItemHighlightMixin {

    // You might need to shadow fields from HandledScreen to get context like x/y/width/height
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    @Shadow @Nullable protected Slot focusedSlot;

    @Shadow public abstract @Nullable Slot getSlotAt(double x, double y);

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        try {
            // The keyPressed method now receives a KeyInput record object.
            if (CoflModClient.uploadItemKeyBinding.matchesKey(input)) {
                if (focusedSlot != null && focusedSlot.hasStack()) {
                    CoflModClient.uploadItem(focusedSlot.getStack());
                }
            }
        } catch (Exception e) {
            System.out.println("[ItemHighlightMixin] keyPressed failed: " + e.getMessage());
        }
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void yourmodid_onDrawBackground(DrawContext context, Slot slot, int x, int y, CallbackInfo ci) {
        try {
            if (!slot.hasStack())
                return;
            DescriptionHandler.DescModification[] tooltips = DescriptionHandler.getTooltipData(CoflModClient.getIdFromStack(slot.getStack()));
            if(tooltips == null)
                return;
            for (DescriptionHandler.DescModification tooltip : tooltips) {
                if (tooltip.type.equals("HIGHLIGHT")) {
                    int hexColor = Integer.parseInt(tooltip.value, 16) | 0xFF000000; // Ensure alpha is set to fully opaque
                    RenderUtils.drawRect(context, slot.x, slot.y, 16, 16, hexColor);
                }
            }
        } catch (Exception e) {
            System.out.println("[ItemHighlightMixin] drawSlot failed: " + e.getMessage());
        }

    }
}