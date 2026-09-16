package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import com.coflnet.PerfTracer;
import com.coflnet.gui.trade.TradePriceCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Mixin(ClientPacketListener.class)
public class NewItemInChestMixin {

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void onSlotUpdateHead(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        // Track UUID changes before the slot is updated
        long perfStart = PerfTracer.begin();
        try {
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.containerMenu == null)
                return;
            
            int slot = packet.getSlot();
            if (slot < 0 || slot >= Minecraft.getInstance().player.containerMenu.slots.size())
                return;
                
            ItemStack previousStack = Minecraft.getInstance().player.containerMenu.getSlot(slot).getItem();
            ItemStack newStack = packet.getItem();
            
            if (previousStack.isEmpty() || newStack.isEmpty())
                return;
            
            Component prevName = previousStack.getCustomName();
            Component newName = newStack.getCustomName();
            
            // If item name is the same (including style/color) but UUIDs differ, map new UUID to original.
            // Using Component.equals() which compares contents, style, and siblings —
            // this prevents remapping between items that share the same plain text name
            // but differ in color (e.g. pets of different tiers like RARE vs MYTHIC).
            if (prevName != null && prevName.equals(newName)) {
                String prevUuid = CoflModClient.getUuidFromStack(previousStack);
                String newUuid = CoflModClient.getUuidFromStack(newStack);
                
                if (prevUuid != null && newUuid != null && !prevUuid.equals(newUuid)) {
                    // Find the original UUID (follow chain if exists)
                    String originalUuid = CoflModClient.uuidToOriginalUuid.getOrDefault(prevUuid, prevUuid);
                    CoflModClient.uuidToOriginalUuid.put(newUuid, originalUuid);
                    // A new stackId now resolves to already-loaded descriptions -
                    // invalidate per-slot caches (e.g. ItemHighlightMixin).
                    CoflModClient.descriptionsVersion.incrementAndGet();
                }
            }
        } catch (Exception e) {
            // Silently ignore errors in UUID tracking
        } finally {
            PerfTracer.end("newItemInChestMixin.onSlotUpdateHead", perfStart);
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onPacketReceive(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        long perfStart = PerfTracer.begin();
        try {
            refreshBazaarOrders(packet.getContainerId());
            int slot = packet.getSlot();
            // Offer slots are 0-35; slot 40 may be the final divider update
            // that makes the full trade layout verifiable.
            if ((slot >= 0 && slot < 36) || slot == 40) {
                TradePriceCache.requestCurrentTrade(packet.getContainerId());
                CoflModClient.openTradeOverlayIfReady(packet.getContainerId());
                return;
            }

            // Non-trade path: only worth the (getCustomName/getString) work below for
            // the handful of menus that need an immediate description refresh; every
            // other slot update packet (the vast majority - regular inventories,
            // hoppers, etc.) returns above without allocating anything.
            Component customName = packet.getItem().getCustomName();
            if (customName == null) {
                return;
            }
            String itemTitle = customName.getString();
            if (itemTitle.contains("Combine Items") // anvil result
                    || itemTitle.equals("§aFlip Order") // bazaar order flip prices loaded
                    || itemTitle.contains("AUCTION FOR") // putting item in auction create
            ) {
                try {
                    if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> hs)
                        CoflModClient.instance.loadDescriptionsForInv(hs);
                } catch (Exception inner) {
                    System.out.println("[NewItemInChestMixin] loadDescriptionsForInv failed: " + inner.getMessage());
                }
            }
        } catch (Exception e) {
            // If it fails, it might be a custom packet or a different type.
            // You can log the exception or handle it as needed.
            System.out.println("[NewItemInChestMixin] Failed to process packet: " + e.getMessage());
        } finally {
            PerfTracer.end("newItemInChestMixin.onPacketReceive", perfStart);
        }
    }

    /** Price the initial offer as soon as Minecraft has applied its complete contents. */
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        long perfStart = PerfTracer.begin();
        try {
            refreshBazaarOrders(packet.containerId());
            TradePriceCache.requestCurrentTrade(packet.containerId());
            CoflModClient.openTradeOverlayIfReady(packet.containerId());
        } finally {
            PerfTracer.end("newItemInChestMixin.onContainerContent", perfStart);
        }
    }

    private static void refreshBazaarOrders(int containerId) {
        if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().containerId == containerId
                && com.coflnet.core.MenuClassifier.isBazaarOrders(screen.getTitle().getString())) {
            CoflModClient.instance.loadDescriptionsForInv(screen);
        }
    }
}
