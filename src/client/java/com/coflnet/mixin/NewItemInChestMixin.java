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
import com.coflnet.core.ChestChangeDebouncer;
import com.coflnet.core.MenuClassifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    /** Set on the client thread in HEAD when the packet really changes the slot's content. */
    @Unique
    private boolean skycofl$chestSlotChanged;

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void skycofl$detectSlotChange(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        // HEAD also runs on the netty thread before the packet is rescheduled; only the client-thread pass counts.
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isSameThread() || mc.player == null)
                return;
            skycofl$chestSlotChanged = false;
            var menu = mc.player.containerMenu;
            int slot = packet.getSlot();
            if (menu == null || menu.containerId != packet.getContainerId() || slot < 0 || slot >= menu.slots.size())
                return;
            skycofl$chestSlotChanged = !ItemStack.matches(menu.getSlot(slot).getItem(), packet.getItem());
        } catch (Exception ignored) {
            // best effort
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
            }

            // Contents of the open chest GUI changed (e.g. a new item in the create-auction
            // listing slot, anvil result, flip order): reload descriptions (debounced, off-thread).
            // Must not be skipped for slots < 36 - the listing slot is one of them.
            boolean changed = skycofl$chestSlotChanged;
            skycofl$chestSlotChanged = false;
            // Cheap gates first; title/name work only for real changes of chest slots.
            if (changed && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> hs
                    && hs.getMenu().containerId == packet.getContainerId()) {
                int total = hs.getMenu().slots.size();
                String title = hs.getTitle().getString();
                if (ChestChangeDebouncer.needsReload(slot, total, true, MenuClassifier.isTradeTitle(total - 36, title))) {
                    Component name = MenuClassifier.isCreateAuction(title) ? null : packet.getItem().getCustomName();
                    if (MenuClassifier.shouldReloadOnChestChange(title, name == null ? null : name.getString()))
                        CoflModClient.instance.onChestContentChanged(hs);
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
            // A full content packet may be the answer to an inventory click in the listing menu.
            if (Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> hs
                    && hs.getMenu().containerId == packet.containerId()
                    && MenuClassifier.isCreateAuction(hs.getTitle().getString())) {
                CoflModClient.instance.onChestContentChanged(hs);
            }
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
