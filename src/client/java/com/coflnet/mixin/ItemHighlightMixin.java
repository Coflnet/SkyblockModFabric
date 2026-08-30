package com.coflnet.mixin;

import CoflCore.handlers.DescriptionHandler;
import com.coflnet.CoflModClient;
import com.coflnet.PerfTracer;
import com.coflnet.gui.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(AbstractContainerScreen.class) // Target AbstractContainerScreen, which is the base for most container UIs
public abstract class ItemHighlightMixin {

    // You might need to shadow fields from AbstractContainerScreen to get context like x/y/width/height
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;

    @Shadow @Nullable protected Slot hoveredSlot;

    // Per-screen-instance cache for extractSlots (called every frame): a slot's
    // highlight is only recomputed when its ItemStack identity changed or the
    // shared descriptionsVersion counter moved, instead of re-scanning tooltip
    // data and re-parsing the highlight color every frame for every slot.
    private ItemStack[] cofl_lastStack;
    private int[] cofl_highlightColor; // 0 means "no highlight"; real colors always have the alpha byte set (0xFF......)
    private boolean[] cofl_highlightOutline;
    private long cofl_lastDescriptionsVersion = -1;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        try {
            // The keyPressed method now receives a KeyInput record object.
            if (CoflModClient.uploadItemKeyBinding.matches(input)) {
                if (hoveredSlot != null && hoveredSlot.hasItem()) {
                    CoflModClient.uploadItem(hoveredSlot.getItem());
                }
            }
        } catch (Exception e) {
            System.out.println("[ItemHighlightMixin] keyPressed failed: " + e.getMessage());
        }
    }

    @Inject(method = "extractSlots", at = @At("HEAD"))
    private void yourmodid_onDrawBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        long perfStart = PerfTracer.begin();
        try {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
            var slots = screen.getMenu().slots;
            int slotCount = slots.size();

            long currentVersion = CoflModClient.descriptionsVersion.get();
            boolean versionChanged = currentVersion != cofl_lastDescriptionsVersion;
            if (cofl_lastStack == null || cofl_lastStack.length != slotCount) {
                cofl_lastStack = new ItemStack[slotCount];
                cofl_highlightColor = new int[slotCount];
                cofl_highlightOutline = new boolean[slotCount];
                versionChanged = true;
            }
            cofl_lastDescriptionsVersion = currentVersion;

            for (int i = 0; i < slotCount; i++) {
                Slot slot = slots.get(i);
                ItemStack stack = slot.getItem();

                if (versionChanged || cofl_lastStack[i] != stack) {
                    cofl_lastStack[i] = stack;
                    cofl_highlightColor[i] = 0;
                    if (!stack.isEmpty()) {
                        DescriptionHandler.DescModification[] tooltips = CoflModClient.getMappedTooltipData(
                                CoflModClient.getIdFromStack(stack));
                        if (tooltips != null) {
                            for (DescriptionHandler.DescModification tooltip : tooltips) {
                                if (tooltip.type.equals("HIGHLIGHT")) {
                                    int hexColor = Integer.parseInt(tooltip.value, 16) | 0xFF000000;
                                    cofl_highlightColor[i] = hexColor;
                                    cofl_highlightOutline[i] = tooltip.line < 0;
                                }
                            }
                        }
                    }
                }

                if (cofl_highlightColor[i] != 0) {
                    if (cofl_highlightOutline[i]) {
                        RenderUtils.drawRectOutline(context, slot.x, slot.y, 16, 16, 2, 0x00000000, cofl_highlightColor[i]);
                    } else {
                        RenderUtils.drawRect(context, slot.x, slot.y, 16, 16, cofl_highlightColor[i]);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[ItemHighlightMixin] extractSlots failed: " + e.getMessage());
        } finally {
            PerfTracer.end("itemHighlightMixin.extractSlots", perfStart);
        }
    }
}
