package com.coflnet.lore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A non-destructive wrapper around the backend {@code DescriptionSetting} object,
 * the whole lore settings model the coflnet server syncs.
 *
 * The backend now exposes this object as JSON: {@code /cofl lore json} sends it
 * back (as a {@code loreSettings} socket message) and running {@code /cofl lore}
 * with the json as its argument saves the whole object at once. that single
 * atomic write replaces the entire stored object so anything we omit is reset to
 * its default lowball config colour replacements highlight settings the
 * disabled flag everything.
 *
 * to edit safely we therefore keep the full parsed json around and only mutate
 * the two members we own: {@code fields} (the line/field layout) and
 * {@code customFormat} (our opaque styling blob). Every other member is carried
 * through untouched even members the mod does not know about so a future
 * backend field can never be wiped by a save from this client. this replaces the
 * old chat menu scraping one command at a time driver entirely.
 */
public final class DescriptionSettings {

    /**
     * Compact, non-HTML-escaping gson that PRESERVES null members ({@code
     * serializenulls so the whole backend object round trips byte for byte a
     * null sibling the backend sent must be written back as null not dropped so
     * a save can never silently alter another setting. the and colour codes
     * also survive no html escaping .
     */
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private final JsonObject root;

    private DescriptionSettings(JsonObject root) {
        this.root = root;
    }

    /** parses the backend json. returns null when the text is not a json object. */
    public static DescriptionSettings parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) {
                return null;
            }
            return new DescriptionSettings(el.getAsJsonObject());
        } catch (RuntimeException exception) {
            System.out.println("[Lore] could not parse DescriptionSetting json: " + exception);
            return null;
        }
    }

    /**
     * a deep copy so a write can build its payload without mutating the last
     * confirmed object. the shared object is only promoted after the backend
     * accepts the save so a rejected write can never leave a stale current.
     */
    public DescriptionSettings copy() {
        return new DescriptionSettings(root.deepCopy());
    }

    /**
     * whether the object actually carries a fields array. lets a caller tell a
     * real but empty layout from a payload that simply omitted fields so a
     * malformed or partial reply can never wipe the users saved layout.
     */
    public boolean hasFields() {
        String key = actualKey("Fields");
        JsonElement el = key == null ? null : root.get(key);
        if (el == null || !el.isJsonArray()) {
            return false;
        }
        for (JsonElement line : el.getAsJsonArray()) {
            if (line == null || !line.isJsonArray()) {
                return false;
            }
            for (JsonElement field : line.getAsJsonArray()) {
                if (field == null || !field.isJsonPrimitive()
                        || !field.getAsJsonPrimitive().isString()
                        || field.getAsString().isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * resolves a member name case insensitively and returns the actual key present
     * on the object, or {@code null} when absent. The backend's socket serializer
     * uses PascalCase ({@code "Fields"}, {@code "CustomFormat"}) while the REST
     * swagger shows camelcase this makes the holder tolerate either so a read
     * works and critically a write overwrites the existing key instead of
     * adding a duplicate differently cased one.
     */
    private String actualKey(String name) {
        if (root.has(name)) {
            return name;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String k : root.keySet()) {
            if (k.toLowerCase(java.util.Locale.ROOT).equals(lower)) {
                return k;
            }
        }
        return null;
    }

    /**
     * the field layout one inner list per line each holding the backend field
     * keys on that line in order. Reads the {@code Fields}/{@code fields} member,
     * tolerating a missing or malformed array returns an empty layout .
     */
    public List<List<String>> getFields() {
        List<List<String>> layout = new ArrayList<>();
        String key = actualKey("Fields");
        JsonElement fieldsEl = key == null ? null : root.get(key);
        if (fieldsEl == null || !fieldsEl.isJsonArray()) {
            return layout;
        }
        for (JsonElement lineEl : fieldsEl.getAsJsonArray()) {
            if (lineEl == null || !lineEl.isJsonArray()) {
                continue;   // ignore a non array element do not synthesize an empty line.
            }
            List<String> line = new ArrayList<>();
            for (JsonElement f : lineEl.getAsJsonArray()) {
                if (f != null && f.isJsonPrimitive()) {
                    String v = f.getAsString();
                    if (v != null && !v.isBlank()) {
                        line.add(v.trim());
                    }
                }
            }
            layout.add(line);
        }
        return layout;
    }

    /**
     * replaces the field layout drops empty tail lines . writes to the existing
     * member key if the object already has one preserving the backends casing
     * otherwise creates {@code "Fields"} to match the backend's PascalCase socket
     * form. Never leaves a duplicate {@code fields}/{@code Fields} pair.
     */
    public void setFields(List<List<String>> layout) {
        JsonArray fields = new JsonArray();
        if (layout != null) {
            int lastNonEmpty = -1;
            for (int i = 0; i < layout.size(); i++) {
                List<String> line = layout.get(i);
                if (line != null && !line.isEmpty()) {
                    lastNonEmpty = i;
                }
            }
            for (int i = 0; i <= lastNonEmpty; i++) {
                JsonArray lineArr = new JsonArray();
                List<String> line = layout.get(i);
                if (line != null) {
                    for (String f : line) {
                        if (f != null && !f.isBlank()) {
                            lineArr.add(f.trim());
                        }
                    }
                }
                fields.add(lineArr);
            }
        }
        String key = actualKey("Fields");
        replaceMember(key != null ? key : "Fields", "Fields", fields);
    }

    /** the opaque styling blob the mod stores its per field templates or null. */
    public String getCustomFormat() {
        String key = actualKey("CustomFormat");
        JsonElement el = key == null ? null : root.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return null;
        }
        String s = el.getAsString();
        return (s == null || s.isBlank()) ? null : s;
    }

    /**
     * sets or clears when null blank the styling blob. overwrites the existing
     * member key if present preserving casing otherwise creates
     * {@code "CustomFormat"} to match the backend's PascalCase form.
     */
    public void setCustomFormat(String customFormat) {
        String key = actualKey("CustomFormat");
        String target = key != null ? key : "CustomFormat";
        if (customFormat == null || customFormat.isBlank()) {
            replaceMember(target, "CustomFormat", com.google.gson.JsonNull.INSTANCE);
        } else {
            replaceMember(target, "CustomFormat", GSON.toJsonTree(customFormat));
        }
    }

    private void replaceMember(String target, String logicalName, JsonElement value) {
        List<String> duplicates = new ArrayList<>();
        for (String key : root.keySet()) {
            if (key.equalsIgnoreCase(logicalName) && !key.equals(target)) {
                duplicates.add(key);
            }
        }
        for (String duplicate : duplicates) {
            root.remove(duplicate);
        }
        root.add(target, value);
    }

    /**
     * every top level member except the layout and styling members as a
     * name serialized value map. used to prove a save preserved all the other
     * settings lowball colours disabled ... the two members the mod owns
     * are excluded case insensitively because those are the only ones a save is
     * allowed to change.
     */
    public java.util.Map<String, String> otherMembers() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String k : root.keySet()) {
            String lower = k.toLowerCase(java.util.Locale.ROOT);
            if ("fields".equals(lower) || "customformat".equals(lower)) {
                continue;
            }
            JsonElement v = root.get(k);
            m.put(k, v == null ? "null" : v.toString());
        }
        return m;
    }

    /** serialises the whole object back to compact json for the write command. */
    public String toJson() {
        return GSON.toJson(root);
    }
}
