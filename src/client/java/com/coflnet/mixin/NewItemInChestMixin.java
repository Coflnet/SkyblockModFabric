package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.network.ClientPlayNetworkHandler;

@Mixin(ClientPlayNetworkHandler.class)
public class NewItemInChestMixin {

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
                if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs)
                    CoflModClient.instance.loadDescriptionsForInv(hs);
                System.out.println("Trade Slot Update Packet received." + packet.getStack().getCustomName());
            }

            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.currentScreenHandler != null) {
                ItemStack previousStack = MinecraftClient.getInstance().player.currentScreenHandler.getSlot(packet.getSlot()).getStack();
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
            System.out.println("Failed to process packet: " + e.getMessage());
        }
    }
}