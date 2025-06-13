package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.gui.RenderUtils;
import com.coflnet.gui.cofl.CoflColConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(HandledScreen.class) // Target HandledScreen, which is the base for most container UIs
public abstract class ItemHighlightMixin {

    // You might need to shadow fields from HandledScreen to get context like x/y/width/height
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    // Assuming descriptionHandler is a client-side component, you'd access it differently
    // or pass relevant data. If it's a global instance, you can reference it directly.
    // private YourDescriptionHandler descriptionHandler; // Example if it needs to be instantiated per screen
    // Or, if it's a static utility:
    // import static com.yourmodid.YourModClient.descriptionHandler; // Example if static

    /**
     * Injects custom rendering logic after the default screen background is drawn.
     * This method is often called `method_23880` or similar in older MC versions,
     * but `renderBackground` is common in newer versions.
     * The exact @At value might need adjustment based on the Minecraft version and specific need.
     */

    @Inject(method = "drawSlot", at = @At("RETURN")) // drawBackground is a good place
    private void yourmodid_onDrawBackground(DrawContext context, Slot slot, CallbackInfo ci) {
        if (!slot.hasStack())
            return;
        DescriptionHandler.DescModification[] tooltips = DescriptionHandler.getTooltipData(CoflModClient.getIdFromStack(slot.getStack()));
        for (DescriptionHandler.DescModification tooltip : tooltips) {
            if (tooltip.type.equals("HIGHLIGHT")) {
                int hexColor = Integer.parseInt(tooltip.value, 16) | 0xFF000000; // Ensure alpha is set to fully opaque
                RenderUtils.drawRect(context, slot.x, slot.y, 16, 16, hexColor);
            }
        }

    }
}