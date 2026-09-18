package com.coflnet.mixin;

import CoflCore.network.WSClient;
import com.coflnet.gui.cofl.LoreEditorScreen;
import com.google.gson.JsonParser;
import com.neovisionaries.ws.client.WebSocket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compatibility bridge until the shared core recognizes the existing loreSettings response. */
@Mixin(value = WSClient.class, remap = false)
public class LoreSettingsMixin {
    @Inject(method = "onTextMessage", at = @At("HEAD"), cancellable = true)
    private void coflnet$loreSettings(WebSocket socket, String text, CallbackInfo ci) {
        // Avoid parsing every price/flip message twice.
        if (!text.contains("\"loreSettings\"")) return;
        var message = JsonParser.parseString(text).getAsJsonObject();
        if (!"loreSettings".equals(message.get("type").getAsString())) return;
        var settings = com.coflnet.core.LoreLayout.parseResponse(message.get("data"));
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gui.screen() instanceof LoreEditorScreen editor)
                editor.receiveSettings(settings);
        });
        ci.cancel();
    }
}
