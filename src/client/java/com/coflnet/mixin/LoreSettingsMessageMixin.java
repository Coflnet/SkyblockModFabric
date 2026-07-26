package com.coflnet.mixin;

import CoflCore.network.WSClient;
import com.coflnet.lore.LoreSaveResponse;
import com.coflnet.lore.LoreSettingsPayload;
import com.coflnet.lore.LoreSync;
import com.coflnet.util.JsonNesting;
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
    private static final int MAX_SOCKET_MESSAGE_LENGTH =
            LoreSettingsPayload.MAX_PAYLOAD_LENGTH * 2;

    @Inject(
            method = "onTextMessage(Lcom/neovisionaries/ws/client/WebSocket;Ljava/lang/String;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void cofl$receiveLoreSettings(WebSocket socket, String text, CallbackInfo callback) {
        if (text == null || text.length() > MAX_SOCKET_MESSAGE_LENGTH) {
            return;
        }
        boolean loreCandidate = text.contains("loreSettings");
        boolean responseCandidate = LoreSync.hasPendingSave()
                && (text.contains("Imported settings (check above)")
                || text.contains("Could not parse the arguments for lore"));
        if (!loreCandidate && !responseCandidate) {
            return;
        }
        if (!JsonNesting.isWithinLimit(text, 64)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject message = parsed.getAsJsonObject();
            JsonElement type = message.get("type");
            if (type == null || !type.isJsonPrimitive()
                    || !type.getAsJsonPrimitive().isString()) {
                return;
            }
            JsonElement data = message.get("data");
            WSClient source = (WSClient) (Object) this;
            if ("loreSettings".equals(type.getAsString())) {
                if (data == null || data.isJsonNull()) {
                    System.out.println("[Lore] loreSettings message had no data");
                } else {
                    LoreSync.onBackendJson(source, LoreSettingsPayload.decode(data));
                }
                callback.cancel();
                return;
            }
            LoreSaveResponse.Result result =
                    LoreSaveResponse.classify(type.getAsString(), data);
            if (result != LoreSaveResponse.Result.NONE) {
                LoreSync.observeSaveResponse(source, result);
            }
        } catch (RuntimeException exception) {
            System.out.println("[Lore] could not read loreSettings message: " + exception);
        }
    }
}
