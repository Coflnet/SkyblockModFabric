package com.coflnet.gui.flip;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public record FlipHudData(
        String id,
        String itemName,
        int count,
        long cost,
        long target,
        long worth,
        String finder,
        String render,
        long receivedAt) {

    public static FlipHudData parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject auction = object(root, "auction");
        String itemName = string(auction, "itemName");
        if (itemName.isBlank()) {
            itemName = messageFallback(root);
        }
        return new FlipHudData(
                string(root, "id"),
                itemName.isBlank() ? "new flip" : itemName,
                Math.max(1, integer(auction, "count")),
                firstLong(longValue(auction, "startingBid"), longValue(root, "cost")),
                longValue(root, "target"),
                longValue(root, "worth"),
                string(root, "finder"),
                string(root, "render"),
                System.currentTimeMillis());
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        return value.getAsString();
    }

    private static long longValue(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        return value.getAsLong();
    }

    private static int integer(JsonObject parent, String key) {
        long value = longValue(parent, key);
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static long firstLong(long first, long second) {
        return first > 0L ? first : second;
    }

    private static String messageFallback(JsonObject root) {
        JsonElement value = root.get("messages");
        if (value == null || !value.isJsonArray()) {
            return "";
        }
        JsonArray messages = value.getAsJsonArray();
        StringBuilder text = new StringBuilder();
        for (JsonElement message : messages) {
            if (!message.isJsonObject()) {
                continue;
            }
            String part = string(message.getAsJsonObject(), "text").replaceAll("§.", "").trim();
            if (part.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(part);
        }
        return text.toString();
    }
}
