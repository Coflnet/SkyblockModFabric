package com.coflnet.mixin;

import CoflCore.CoflSkyCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChat(String content, CallbackInfo ci) {
        if (content == null) {
            return;
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("/")) {
            return;
        }

        String[] parts = trimmed.substring(1).split("\\s+");
        if (parts.length < 2 || !"cofl".equalsIgnoreCase(parts[0]) || !"chat".equalsIgnoreCase(parts[1])) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        String username = client.player.getName().getString();
        CoflSkyCommand.processCommand(args, username);
        ci.cancel();
    }
}
