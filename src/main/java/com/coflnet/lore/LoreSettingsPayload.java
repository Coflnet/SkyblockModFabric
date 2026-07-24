package com.coflnet.lore;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public final class LoreSettingsPayload {
    public static final int MAX_PAYLOAD_LENGTH = 262144;

    private LoreSettingsPayload() {
    }

    public static String decode(JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return null;
        }
        if (!data.isJsonPrimitive() || !data.getAsJsonPrimitive().isString()) {
            return bounded(data.toString());
        }
        String value = bounded(data.getAsString());
        if (value == null) {
            return null;
        }
        try {
            JsonElement nested = JsonParser.parseString(value);
            if (nested.isJsonPrimitive() && nested.getAsJsonPrimitive().isString()) {
                return bounded(nested.getAsString());
            }
        } catch (RuntimeException ignored) {
            return value;
        }
        return value;
    }

    private static String bounded(String value) {
        return value != null && value.length() <= MAX_PAYLOAD_LENGTH ? value : null;
    }
}
