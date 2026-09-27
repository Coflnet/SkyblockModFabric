package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler.DescModification;
import com.google.gson.JsonObject;

/** Request-scoped settings only: preview never updates the shared tooltip cache or saved settings. */
public final class LorePreview {
    private LorePreview() {}

    public static DescModification[] load(String nbt, String username, JsonObject settings, int slot) {
        if (slot < 0) throw new IllegalArgumentException("Invalid preview slot");
        DescModification[][] modifications = new DescriptionRequest("Lore preview", new String[slot + 1],
                nbt, username, null).loadWithSettings(settings);
        if (modifications[slot] == null)
            throw new IllegalStateException("No preview returned by the description API");
        return modifications[slot];
    }
}
