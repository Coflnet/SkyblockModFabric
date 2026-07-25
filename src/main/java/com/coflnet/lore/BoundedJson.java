package com.coflnet.lore;

import com.coflnet.util.JsonNesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.Map;

final class BoundedJson {
    private static final int MAX_DEPTH = 64;

    private BoundedJson() {
    }

    static JsonObject parseObject(String json, int maximumLength) {
        if (json == null || json.isBlank() || json.length() > maximumLength
                || !JsonNesting.isWithinLimit(json, MAX_DEPTH)) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() && hasSafeDepth(parsed)
                    ? parsed.getAsJsonObject()
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    static boolean hasSafeDepth(JsonElement root) {
        if (root == null) {
            return false;
        }
        ArrayDeque<Node> pending = new ArrayDeque<>();
        pending.add(new Node(root, 1));
        while (!pending.isEmpty()) {
            Node node = pending.removeLast();
            if (node.depth() > MAX_DEPTH) {
                return false;
            }
            JsonElement value = node.value();
            if (value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> member : value.getAsJsonObject().entrySet()) {
                    if (member.getValue() != null) {
                        pending.add(new Node(member.getValue(), node.depth() + 1));
                    }
                }
            } else if (value.isJsonArray()) {
                for (JsonElement element : value.getAsJsonArray()) {
                    if (element != null) {
                        pending.add(new Node(element, node.depth() + 1));
                    }
                }
            }
        }
        return true;
    }

    private record Node(JsonElement value, int depth) {
    }
}
