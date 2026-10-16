package com.coflnet.mixin;

import com.coflnet.gui.bazaar.BazaarCharts;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class BazaarGraphKeyMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void coflnet$openHoveredGraphs(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (BazaarCharts.handleKey((AbstractContainerScreen<?>) (Object) this, event,
                hoveredSlot == null ? ItemStack.EMPTY : hoveredSlot.getItem())) callback.setReturnValue(true);
    }
}
