package com.coflnet.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class LoreSaveResponse {
    private static final String SUCCESS = "Imported settings (check above)";
    private static final String REJECTION = "Could not parse the arguments for lore";
    private static final int MAX_DEPTH = 6;
    private static final int MAX_VALUES = 1024;

    public enum Result {
        NONE,
        SUCCESS,
        REJECTED
    }

    private LoreSaveResponse() {
    }

    public static Result classify(String type, JsonElement data) {
        if (type == null || data == null) {
            return Result.NONE;
        }
        Search search = new Search();
        if ("chatMessage".equalsIgnoreCase(type)
                && search.contains(data, SUCCESS, true, 0)) {
            return Result.SUCCESS;
        }
        if ("writeToChat".equalsIgnoreCase(type)
                && search.contains(data, REJECTION, false, 0)) {
            return Result.REJECTED;
        }
        return Result.NONE;
    }

    private static final class Search {
        private int visited;

        private boolean contains(JsonElement element, String expected, boolean exact, int depth) {
            if (element == null || element.isJsonNull()
                    || depth > MAX_DEPTH || ++visited > MAX_VALUES) {
                return false;
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (JsonElement value : array) {
                    if (contains(value, expected, exact, depth + 1)) {
                        return true;
                    }
                }
                return false;
            }
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                for (String key : object.keySet()) {
                    if (contains(object.get(key), expected, exact, depth + 1)) {
                        return true;
                    }
                }
                return false;
            }
            if (!element.getAsJsonPrimitive().isString()) {
                return false;
            }
            String value = element.getAsString();
            if (exact ? expected.equals(value) : value.contains(expected)) {
                return true;
            }
            if (value.length() > LoreSettingsPayload.MAX_PAYLOAD_LENGTH) {
                return false;
            }
            String trimmed = value.trim();
            if (!(trimmed.startsWith("{") || trimmed.startsWith("[")
                    || trimmed.startsWith("\""))) {
                return false;
            }
            try {
                return contains(JsonParser.parseString(trimmed), expected, exact, depth + 1);
            } catch (RuntimeException ignored) {
                return false;
            }
        }
    }
}
