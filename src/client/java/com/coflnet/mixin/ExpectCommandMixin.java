package com.coflnet.mixin;

import CoflCore.network.WSClient;
import com.coflnet.core.ExpectCommandParser;
import com.coflnet.core.ExpectedCommandState;
import com.neovisionaries.ws.client.WebSocket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CoflSkyCore's CommandType has no "expectCommand" entry, and WSClient is created inside
 * WSClientWrapper (so it cannot be subclassed). WSClient.onTextMessage is the earliest point that
 * sees the raw text; the message is observed here and left to the core, which logs it as unknown.
 */
@Mixin(value = WSClient.class, remap = false)
public class ExpectCommandMixin {
    @Inject(method = "onTextMessage", at = @At("HEAD"))
    private void coflnet$expectCommand(WebSocket socket, String text, CallbackInfo ci) {
        ExpectCommandParser.parse(text).ifPresent(
                expected -> ExpectedCommandState.set(expected, System.currentTimeMillis()));
    }
}
