package com.coflnet.mixin;

import com.coflnet.gui.bazaar.BazaarCharts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public abstract class BazaarChartTooltipMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "extractTooltip(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("HEAD"), cancellable = true)
    private void coflnet$bazaarChart(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        var screen = (AbstractContainerScreen<?>) (Object) this;
        if (hoveredSlot == null || !hoveredSlot.hasItem() || !screen.getMenu().getCarried().isEmpty()) return;
        var stack = hoveredSlot.getItem();
        var chart = BazaarCharts.chart(stack);
        if (chart == null) return;
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                List.of(stack.getHoverName(), Component.translatable("tooltip.coflmod.chart.controls")),
                Optional.of(chart), x, y, stack.get(DataComponents.TOOLTIP_STYLE));
        ci.cancel();
    }
}
