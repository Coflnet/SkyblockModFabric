package com.coflnet.mixin;

import CoflCore.network.WSClient;
import com.coflnet.lore.LoreSettingsPayload;
import com.coflnet.lore.LoreSync;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neovisionaries.ws.client.WebSocket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WSClient.class, remap = false)
public class LoreSettingsMessageMixin {
    @Inject(
            method = "onTextMessage(Lcom/neovisionaries/ws/client/WebSocket;Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void cofl$receiveLoreSettings(WebSocket socket, String text, CallbackInfo callback) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject message = parsed.getAsJsonObject();
            JsonElement type = message.get("type");
            if (type == null || !type.isJsonPrimitive()
                    || !"loreSettings".equals(type.getAsString())) {
                return;
            }
            JsonElement data = message.get("data");
            if (data == null || data.isJsonNull()) {
                System.out.println("[Lore] loreSettings message had no data");
            } else {
                LoreSync.onBackendJson(LoreSettingsPayload.decode(data));
            }
            callback.cancel();
        } catch (RuntimeException exception) {
            System.out.println("[Lore] could not read loreSettings message: " + exception);
        }
    }
}
