package com.coflnet.core;

import CoflCore.classes.Position;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.configuration.Config;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.google.gson.JsonObject;

/** Captured request data, independent of the screen that supplied it. */
public record DescriptionRequest(String title, String[] itemIds, String inventoryNbt,
                                 String username, Position position) {
    public void load() {
        DescriptionHandler.loadDescriptionForInventory(itemIds, title, inventoryNbt, username, position);
    }

    /** Request scoped fields never modify saved settings or the live tooltip cache. */
    public DescriptionHandler.DescModification[][] loadWithSettings(JsonObject settings) {
        JsonObject request = new JsonObject();
        request.addProperty("chestName", title);
        request.addProperty("version", 4);
        request.addProperty("fullInventoryNbt", inventoryNbt);
        request.add("settings", settings.deepCopy());
        if (Config.ServerContext != null && !Config.ServerContext.isBlank())
            request.addProperty("server", Config.ServerContext);
        if (position != null) request.add("position", WSClient.gson.toJsonTree(position));
        String response = QueryServerCommands.PostRequest(Config.BaseUrl + "/api/mod/description/modifications",
                request.toString(), username);
        var result = WSClient.gson.fromJson(response, DescriptionHandler.DescModification[][].class);
        if (result == null || result.length < itemIds.length)
            throw new IllegalStateException("Incomplete response from the description API");
        return java.util.Arrays.copyOf(result, itemIds.length);
    }

    public DescriptionHandler.DescModification[][] loadFullCraftCosts() {
        JsonObject settings = new JsonObject();
        settings.add("Fields", com.google.gson.JsonParser.parseString("[[\"FullCraftCost\"]]"));
        settings.addProperty("Disabled", false);
        return loadWithSettings(settings);
    }

    /** Preserve positional prices even when distinct items have the same tooltip cache ID. */
    public DescriptionHandler.DescModification[][] loadBySlot() {
        String prefix = "trade:" + java.util.UUID.randomUUID() + ":";
        String[] slotIds = new String[itemIds.length];
        java.util.Arrays.setAll(slotIds, slot -> prefix + slot);
        try {
            DescriptionHandler.loadDescriptionForInventory(slotIds, title, inventoryNbt, username, position);
            var result = new DescriptionHandler.DescModification[itemIds.length][];
            for (int slot = 0; slot < itemIds.length; slot++) {
                result[slot] = DescriptionHandler.getTooltipData(slotIds[slot]);
            }
            return result;
        } finally {
            for (String id : slotIds) DescriptionHandler.tooltipItemIdMap.remove(id);
        }
    }

    /** Never wait for the network on the caller, including when no throttle delay is needed. */
    public void submit(long delayMs, Runnable onLoaded) {
        submit(delayMs, onLoaded, () -> {});
    }

    public void submit(long delayMs, Runnable onLoaded, Runnable onFailed) {
        Thread.ofVirtual().name("CoflSky-Descriptions").start(() -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                load();
                onLoaded.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                System.err.println("[descriptions] Failed to load " + title + "; refresh can be retried: " + e);
                onFailed.run();
            }
        });
    }
}
