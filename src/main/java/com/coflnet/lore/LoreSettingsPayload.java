package com.coflnet.lore;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class LoreSettingsPayload {
    private LoreSettingsPayload() {
    }

    public static String decode(JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return null;
        }
        if (!data.isJsonPrimitive() || !data.getAsJsonPrimitive().isString()) {
            return data.toString();
        }
        String value = data.getAsString();
        try {
            JsonElement nested = JsonParser.parseString(value);
            if (nested.isJsonPrimitive() && nested.getAsJsonPrimitive().isString()) {
                return nested.getAsString();
            }
        } catch (RuntimeException ignored) {
            return value;
        }
        return value;
    }
}
