package com.coflnet.mixin;

import com.coflnet.CoflModClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TabListCustomisationMixin {
    @Inject(method = "handleTabListCustomisation", at = @At("TAIL"))
    private void coflnet$tabListHeaderFooter(ClientboundTabListPacket packet, CallbackInfo ci) {
        CoflModClient.onTabListCustomisation(packet.header().getString(), packet.footer().getString());
    }
}
