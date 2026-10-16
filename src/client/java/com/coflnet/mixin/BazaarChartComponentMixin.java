package com.coflnet.mixin;

import com.coflnet.gui.bazaar.BazaarChartRenderer;
import com.coflnet.gui.bazaar.BazaarChartTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientTooltipComponent.class)
public interface BazaarChartComponentMixin {
    @Inject(method = "create(Lnet/minecraft/world/inventory/tooltip/TooltipComponent;)Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;",
            at = @At("HEAD"), cancellable = true)
    private static void coflnet$bazaarChart(TooltipComponent data, CallbackInfoReturnable<ClientTooltipComponent> cir) {
        if (data instanceof BazaarChartTooltip chart) cir.setReturnValue(new BazaarChartRenderer(chart));
    }
}
