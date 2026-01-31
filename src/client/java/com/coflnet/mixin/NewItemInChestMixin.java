package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.network.ClientPlayNetworkHandler;

@Mixin(ClientPlayNetworkHandler.class)
public class NewItemInChestMixin {
    
    private static final Gson gson = new Gson();

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void onSlotUpdateHead(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        // Track UUID changes before the slot is updated
        try {
            if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().player.currentScreenHandler == null)
                return;
            
            int slot = packet.getSlot();
            if (slot < 0 || slot >= MinecraftClient.getInstance().player.currentScreenHandler.slots.size())
                return;
                
            ItemStack previousStack = MinecraftClient.getInstance().player.currentScreenHandler.getSlot(slot).getStack();
            ItemStack newStack = packet.getStack();
            
            if (previousStack.isEmpty() || newStack.isEmpty())
                return;
            
            String prevTitle = previousStack.getCustomName() != null ? previousStack.getCustomName().getString() : "";
            String newTitle = newStack.getCustomName() != null ? newStack.getCustomName().getString() : "";
            
            // If item title is the same but UUIDs differ, map new UUID to original
            if (!prevTitle.isEmpty() && prevTitle.equals(newTitle)) {
                String prevUuid = extractUuid(previousStack);
                String newUuid = extractUuid(newStack);
                
                if (prevUuid != null && newUuid != null && !prevUuid.equals(newUuid)) {
                    // Find the original UUID (follow chain if exists)
                    String originalUuid = CoflModClient.uuidToOriginalUuid.getOrDefault(prevUuid, prevUuid);
                    CoflModClient.uuidToOriginalUuid.put(newUuid, originalUuid);
                }
            }
        } catch (Exception e) {
            // Silently ignore errors in UUID tracking
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void onPacketReceive(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            String itemTitle = packet.getStack().getCustomName() != null ? packet.getStack().getCustomName().getString() : "";
            if (!itemTitle.isEmpty() && (
                    itemTitle.contains("Pending their confirm") || itemTitle.contains("Deal timer!") // trade window
                    || itemTitle.contains("Combine Items") // anvil result
                    || itemTitle.equals("§aFlip Order") // bazaar order flip prices loaded
            || itemTitle.contains("AUCTION FOR") // putting item in auction create
            )) {
                try {
                    if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs)
                        CoflModClient.instance.loadDescriptionsForInv(hs);
                    System.out.println("Trade Slot Update Packet received." + packet.getStack().getCustomName());
                } catch (Exception inner) {
                    System.out.println("[NewItemInChestMixin] loadDescriptionsForInv failed: " + inner.getMessage());
                }
            }

            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.currentScreenHandler != null) {
                int slot = packet.getSlot();
                if (slot < 0 || slot >= MinecraftClient.getInstance().player.currentScreenHandler.slots.size())
                    return;
                    
                ItemStack previousStack = MinecraftClient.getInstance().player.currentScreenHandler.getSlot(slot).getStack();
                if(previousStack.get(DataComponentTypes.LORE) == null)
                    return;
                for (Text line : previousStack.get(DataComponentTypes.LORE).lines()) {
                    if(line.getString().contains("Refreshing"))
                    {
                        // TODO: try batching this to refresh lore sooner than current waittime
                    }
                }
            }
        } catch (Exception e) {
            // If it fails, it might be a custom packet or a different type.
            // You can log the exception or handle it as needed.
            System.out.println("[NewItemInChestMixin] Failed to process packet: " + e.getMessage());
        }
    }
    
    private String extractUuid(ItemStack stack) {
        for (ComponentType<?> type : stack.getComponents().getTypes()) {
            if (type.toString().contains("minecraft:custom_data")) {
                JsonObject stackJson = gson.fromJson(stack.get(type).toString(), JsonObject.class);
                if (stackJson != null) {
                    JsonElement uuid = stackJson.get("uuid");
                    if (uuid != null) {
                        return uuid.getAsString();
                    }
                }
            }
        }
        return null;
    }
}