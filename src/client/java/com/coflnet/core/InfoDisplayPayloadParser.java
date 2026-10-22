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
        long id;
        try {
            id = obj.get("id").getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            throw new ParseException("'id' must be an integer");
        }
        if (id < MIN_ID || id > MAX_ID) {
            throw new ParseException("'id' must be between " + MIN_ID + " and " + MAX_ID + " (was " + id + ")");
        }

        boolean clear = obj.has("clear") && !obj.get("clear").isJsonNull() && asBoolean(obj.get("clear"));

        String title = stringOrNull(obj, "title");

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
            ttl = parseTtl(obj.get("ttl"));
        }

        return new InfoDisplayPayload((int) id, title, lines, ttl, clear);
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

    /** Reads the seconds as a long so a value past the int range stays long-lived instead of wrapping to 0 (permanent). */
    private static int parseTtl(JsonElement el) throws ParseException {
        long ttl;
        try {
            ttl = el.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            throw new ParseException("'ttl' must be an integer number of seconds");
        }
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, ttl));
    }

    private static String stringOrNull(JsonObject obj, String key) throws ParseException {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        if (!obj.get(key).isJsonPrimitive()) {
            throw new ParseException("'" + key + "' must be a string");
        }
        return obj.get(key).getAsString();
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
