package com.coflnet.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON payload for a permanent HUD "info display" pushed from the
 * backend (or typed manually via {@code /cofl display <json>}):
 * <pre>
 * {"id":1,"title":"&sect;6Flips","lines":["&sect;aline" | {"text":"...","hover":"...","onClick":"..."}],"ttl":30,"clear":false}
 * </pre>
 * {@code id} (1-3) is the only required field. Defensive caps keep a malformed
 * or hostile payload from blowing up the HUD: at most {@link #MAX_LINES} lines,
 * each truncated to {@link #MAX_LINE_LENGTH} characters.
 */
public final class InfoDisplayPayloadParser {
    public static final int MIN_ID = 1;
    public static final int MAX_ID = 3;
    public static final int MAX_LINES = 30;
    public static final int MAX_LINE_LENGTH = 200;

    private InfoDisplayPayloadParser() {
    }

    /** Thrown for any malformed or invalid payload; {@link #getMessage()} is safe to show to the user. */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    public static InfoDisplayPayload parse(String json) throws ParseException {
        if (json == null || json.isBlank()) {
            throw new ParseException("Empty payload");
        }

        JsonObject obj;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new ParseException("Payload must be a JSON object");
            }
            obj = parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new ParseException("Malformed JSON: " + e.getMessage());
        }

        if (!obj.has("id") || obj.get("id").isJsonNull() || !obj.get("id").isJsonPrimitive()) {
            throw new ParseException("Missing required field 'id'");
        }
        int id;
        try {
            id = obj.get("id").getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            throw new ParseException("'id' must be an integer");
        }
        if (id < MIN_ID || id > MAX_ID) {
            throw new ParseException("'id' must be between " + MIN_ID + " and " + MAX_ID + " (was " + id + ")");
        }

        boolean clear = obj.has("clear") && !obj.get("clear").isJsonNull() && asBoolean(obj.get("clear"));

        String title = null;
        if (obj.has("title") && !obj.get("title").isJsonNull()) {
            title = obj.get("title").getAsString();
        }

        List<InfoDisplayPayload.InfoDisplayLine> lines = new ArrayList<>();
        if (obj.has("lines") && !obj.get("lines").isJsonNull()) {
            JsonElement linesEl = obj.get("lines");
            if (!linesEl.isJsonArray()) {
                throw new ParseException("'lines' must be an array");
            }
            JsonArray arr = linesEl.getAsJsonArray();
            for (JsonElement el : arr) {
                if (lines.size() >= MAX_LINES) {
                    break; // defensive cap; silently drop the rest rather than fail the whole payload
                }
                lines.add(parseLine(el));
            }
        }

        int ttl = 0;
        if (obj.has("ttl") && !obj.get("ttl").isJsonNull()) {
            try {
                ttl = obj.get("ttl").getAsInt();
            } catch (NumberFormatException | UnsupportedOperationException e) {
                throw new ParseException("'ttl' must be an integer number of seconds");
            }
            if (ttl < 0) {
                ttl = 0;
            }
        }

        return new InfoDisplayPayload(id, title, lines, ttl, clear);
    }

    private static InfoDisplayPayload.InfoDisplayLine parseLine(JsonElement el) throws ParseException {
        if (el.isJsonPrimitive()) {
            return new InfoDisplayPayload.InfoDisplayLine(capLength(el.getAsString()), null, null);
        }
        if (el.isJsonObject()) {
            JsonObject lineObj = el.getAsJsonObject();
            String text = stringOrNull(lineObj, "text");
            String hover = stringOrNull(lineObj, "hover");
            String onClick = stringOrNull(lineObj, "onClick");
            return new InfoDisplayPayload.InfoDisplayLine(capLength(text == null ? "" : text), hover, onClick);
        }
        throw new ParseException("Each line must be a string or an object with a 'text' field");
    }

    private static String stringOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static boolean asBoolean(JsonElement el) {
        try {
            return el.getAsBoolean();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return false;
        }
    }

    private static String capLength(String text) {
        return text.length() > MAX_LINE_LENGTH ? text.substring(0, MAX_LINE_LENGTH) : text;
    }
}
