package com.coflnet.core;

import CoflCore.configuration.Config;
import CoflCore.handlers.DescriptionHandler.DescModification;
import CoflCore.network.QueryServerCommands;
import CoflCore.network.WSClient;
import com.google.gson.JsonObject;

/** Request-scoped settings only: preview never updates the shared tooltip cache or saved settings. */
public final class LorePreview {
    private LorePreview() {}

    public static DescModification[] load(String nbt, String username, JsonObject settings, int slot) {
        JsonObject request = new JsonObject();
        request.addProperty("chestName", "Lore preview");
        request.addProperty("version", 4);
        request.addProperty("fullInventoryNbt", nbt);
        request.add("settings", settings.deepCopy());
        String response = QueryServerCommands.PostRequest(Config.BaseUrl + "/api/mod/description/modifications",
                request.toString(), username);
        DescModification[][] modifications = WSClient.gson.fromJson(response, DescModification[][].class);
        if (modifications == null || slot < 0 || slot >= modifications.length || modifications[slot] == null)
            throw new IllegalStateException("No preview returned by the description API");
        return modifications[slot];
    }
}
