package com.coflnet.core;

import CoflCore.classes.Position;
import CoflCore.configuration.Config;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.google.gson.JsonObject;

/** Inventory and context captured on the client thread; fetching never mutates displayed data. */
public record DescriptionRequest(String title, String[] itemIds, String inventoryNbt,
                                 String username, Position position, String server, String baseUrl, boolean forceGoalRefresh) {
    public DescriptionRequest(String title, String[] itemIds, String inventoryNbt, String username,
                              Position position, String server, String baseUrl) {
        this(title, itemIds, inventoryNbt, username, position, server, baseUrl, false);
    }

    public DescriptionRequest(String title, String[] itemIds, String inventoryNbt, String username, Position position) {
        this(title, itemIds.clone(), inventoryNbt, username, position == null ? null
                : new Position(position.getX(), position.getY(), position.getZ()), Config.ServerContext, Config.BaseUrl);
    }

    public record Destination(String title, String username, String server, String baseUrl,
                              Integer x, Integer y, Integer z) {}

    public Destination destination() {
        return new Destination(title, username, server, baseUrl, position == null ? null : position.getX(),
                position == null ? null : position.getY(), position == null ? null : position.getZ());
    }

    public boolean sameSnapshot(DescriptionRequest other) {
        return other != null && destination().equals(other.destination()) && inventoryNbt.equals(other.inventoryNbt)
                && java.util.Arrays.equals(itemIds, other.itemIds);
    }

    public DescriptionRequest withGoalRefresh() {
        return forceGoalRefresh ? this : new DescriptionRequest(title, itemIds, inventoryNbt, username, position, server, baseUrl, true);
    }

    public DescriptionHandler.DescModification[][] fetch() {
        JsonObject body = new JsonObject();
        body.addProperty("chestName", title);
        body.addProperty("version", 4);
        body.addProperty("fullInventoryNbt", inventoryNbt);
        if (forceGoalRefresh) body.addProperty("forceGoalRefresh", true);
        if (server != null && !server.isBlank()) body.addProperty("server", server);
        if (position != null) body.add("position", WSClient.gson.toJsonTree(position, Position.class));
        String response = QueryServerCommands.PostRequest(baseUrl + "/api/mod/description/modifications", body.toString(), username);
        var result = WSClient.gson.fromJson(response, DescriptionHandler.DescModification[][].class);
        if (result == null || result.length < itemIds.length)
            throw new IllegalStateException("Description response is missing inventory slots");
        for (var slot : result) {
            if (slot == null) continue;
            for (var modification : slot) {
                if (modification == null || modification.type == null)
                    throw new IllegalStateException("Invalid description modification");
            }
        }
        return result;
    }
}
