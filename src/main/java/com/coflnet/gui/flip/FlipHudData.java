package com.coflnet.gui.flip;

import com.coflnet.util.JsonNesting;
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
        String finder,
        String render,
        long receivedAt) {
    public static final int MAX_PAYLOAD_LENGTH = 65_536;
    private static final int MAX_ITEM_NAME_LENGTH = 160;
    private static final int MAX_ID_LENGTH = 64;
    private static final int MAX_FINDER_LENGTH = 32;
    private static final int MAX_RENDER_LENGTH = 128;
    private static final int MAX_MESSAGE_COUNT = 8;
    private static final int MAX_JSON_DEPTH = 64;

    public static FlipHudData parse(String json) {
        if (json == null || json.length() > MAX_PAYLOAD_LENGTH
                || !JsonNesting.isWithinLimit(json, MAX_JSON_DEPTH)) {
            throw new IllegalArgumentException("flip payload exceeded its structural limits");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "flip payload must be a valid json object", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException(
                    "flip payload must be a valid json object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonObject auction = object(root, "auction");
        String itemName = displayString(auction, "itemName", MAX_ITEM_NAME_LENGTH);
        if (itemName.isBlank()) {
            itemName = messageFallback(root);
        }
        return new FlipHudData(
                auctionId(root),
                itemName.isBlank() ? "new flip" : itemName,
                Math.max(1, integer(auction, "count")),
                firstLong(longValue(auction, "startingBid"), longValue(root, "cost")),
                Math.max(0L, longValue(root, "target")),
                displayString(root, "finder", MAX_FINDER_LENGTH),
                string(root, "render", MAX_RENDER_LENGTH),
                System.currentTimeMillis());
    }

    public static String auctionIdFromCommand(String command) {
        if (command == null || command.length() > 96) {
            return "";
        }
        String[] parts = command.trim().split("\\s+", 2);
        if (parts.length != 2 || !"viewauction".equalsIgnoreCase(parts[0])) {
            return "";
        }
        return normalizeAuctionId(parts[1]);
    }

    public static boolean changesBackendSession(String command) {
        if (command == null || command.length() > 256) {
            return false;
        }
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String root = parts[0];
        if (!"cofl".equalsIgnoreCase(root) && !"cl".equalsIgnoreCase(root)) {
            return false;
        }
        return switch (parts[1].toLowerCase(java.util.Locale.ROOT)) {
            case "start", "stop", "reset" -> true;
            default -> false;
        };
    }

    static String formatCoins(long coins) {
        if (coins >= 999_950_000L) {
            return abbreviated(coins, 1_000_000_000L, 'b');
        }
        if (coins >= 999_950L) {
            return abbreviated(coins, 1_000_000L, 'm');
        }
        if (coins >= 1_000L) {
            return abbreviated(coins, 1_000L, 'k');
        }
        return String.valueOf(coins);
    }

    private static String abbreviated(long coins, long unit, char suffix) {
        long whole = coins / unit;
        long tenth = (coins % unit + unit / 20L) / (unit / 10L);
        if (tenth == 10L) {
            whole++;
            tenth = 0L;
        }
        return whole + "." + tenth + suffix;
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static String string(JsonObject parent, String key, int maximumLength) {
        String text = rawString(parent, key);
        return text.length() <= maximumLength ? text : text.substring(0, maximumLength);
    }

    private static String rawString(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        return value.getAsString();
    }

    private static String auctionId(JsonObject root) {
        return normalizeAuctionId(rawString(root, "id"));
    }

    static String normalizeAuctionId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > MAX_ID_LENGTH
                || normalized.chars().anyMatch(Character::isWhitespace)
                || normalized.chars().anyMatch(Character::isISOControl)) {
            return "";
        }
        return normalized;
    }

    private static String displayString(JsonObject parent, String key, int maximumLength) {
        String value = rawString(parent, key);
        StringBuilder clean = new StringBuilder(Math.min(value.length(), maximumLength));
        for (int i = 0; i < value.length() && clean.length() < maximumLength; i++) {
            char character = value.charAt(i);
            if (character == '§') {
                if (i + 1 < value.length()) {
                    i++;
                }
                continue;
            }
            clean.append(Character.isISOControl(character) ? ' ' : character);
        }
        return clean.toString().strip();
    }

    private static long longValue(JsonObject parent, String key) {
        JsonElement value = parent == null ? null : parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return 0L;
        }
        try {
            java.math.BigDecimal number = value.getAsBigDecimal();
            if (number.signum() < 0
                    || number.stripTrailingZeros().scale() > 0
                    || number.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
                return 0L;
            }
            return number.longValueExact();
        } catch (RuntimeException exception) {
            return 0L;
        }
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
        int count = 0;
        for (JsonElement message : messages) {
            if (count++ >= MAX_MESSAGE_COUNT || text.length() >= MAX_ITEM_NAME_LENGTH) {
                break;
            }
            if (!message.isJsonObject()) {
                continue;
            }
            String part = displayString(
                    message.getAsJsonObject(), "text", MAX_ITEM_NAME_LENGTH);
            if (part.isBlank()) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(part);
        }
        String result = text.toString();
        return result.length() <= MAX_ITEM_NAME_LENGTH
                ? result
                : result.substring(0, MAX_ITEM_NAME_LENGTH);
    }
}
