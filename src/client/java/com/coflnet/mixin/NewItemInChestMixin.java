package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
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
                    itemTitle.contains("Pending their confirm") || itemTitle.contains("Deal timer!")
            || itemTitle.contains("AUCTION FOR"))) {
                if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> hs)
                    CoflModClient.instance.loadDescriptionsForInv(hs);
                System.out.println("Trade Slot Update Packet received." + packet.getStack().getCustomName());
            }
        } catch (Exception e) {
            // If it fails, it might be a custom packet or a different type.
            // You can log the exception or handle it as needed.
            System.out.println("Failed to process packet: " + e.getMessage());
        }
    }
}