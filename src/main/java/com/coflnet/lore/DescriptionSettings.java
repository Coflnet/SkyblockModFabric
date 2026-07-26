package com.coflnet.lore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private static final int MAX_ROWS = 64;
    private static final int MAX_FIELDS_PER_ROW = 64;
    private static final int MAX_TOTAL_FIELDS = 2048;
    private static final int MAX_FIELD_LENGTH = 128;
    private static final Set<String> DESCRIPTION_FIELDS = Set.of(
            "none",
            "lbin",
            "lbin_key",
            "median",
            "median_key",
            "volume",
            "tag",
            "craft_cost",
            "bazaarbuy",
            "bazaarsell",
            "price_paid",
            "item_key",
            "enchantcost",
            "gemvalue",
            "spentonahfees",
            "katupgradecost",
            "instasellprice",
            "modifiercost",
            "fullcraftcost",
            "modifiercostlist",
            "finderestimates",
            "volatility",
            "lastsoldfor",
            "timetosell",
            "npcsellprice",
            "colorcode",
            "defaultlore",
            "aiestimate",
            "bazaar_cost");
    private static final List<String> BOOLEAN_MEMBERS = List.of(
            "HighlightFilterMatch",
            "DisableHighlighting",
            "DisableSuggestions",
            "Disabled",
            "PreferLbinInSuggestions",
            "SuggestQuicksell",
            "NoCookie",
            "BuyOrderPrices",
            "DisableAuctionStartedTime",
            "LowballHideBreakdown",
            "LowballHideWorstCase");
    private static final List<String> BYTE_MEMBERS = List.of(
            "LowballMedUndercut",
            "LowballLbinUndercut",
            "LowballNonExactExtraPct",
            "LowballWorstCaseExtraPct");
    private static final List<String> NULLABLE_STRING_MEMBERS = List.of(
            "ReplaceGrayWith",
            "ReplaceAquaWith",
            "ReplaceYellowWith",
            "ReplaceGoldWith",
            "ReplaceWhiteWith",
            "CustomFormat");
    private static final List<String> NULLABLE_STRING_ARRAY_MEMBERS = List.of(
            "DisableInfoIn",
            "BazaarBookmarks");
    private static final List<String> REQUIRED_MEMBERS = List.of(
            "Fields",
            "HighlightFilterMatch",
            "MinProfitForHighlight",
            "DisableHighlighting",
            "DisableSuggestions",
            "DisableInfoIn",
            "BazaarBookmarks",
            "Disabled",
            "LowballMedUndercut",
            "LowballLbinUndercut",
            "PreferLbinInSuggestions",
            "SuggestQuicksell",
            "ReplaceGrayWith",
            "ReplaceAquaWith",
            "ReplaceYellowWith",
            "ReplaceGoldWith",
            "ReplaceWhiteWith",
            "NoCookie",
            "BuyOrderPrices",
            "DisableAuctionStartedTime",
            "LowballNonExactExtraPct",
            "LowballWorstCaseExtraPct",
            "LowballHideBreakdown",
            "LowballHideWorstCase",
            "CustomFormat",
            "HighlightInfo");

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
        JsonObject root = BoundedJson.parseObject(json, LoreSettingsPayload.MAX_PAYLOAD_LENGTH);
        return root == null ? null : new DescriptionSettings(root);
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
        if (el.getAsJsonArray().size() > MAX_ROWS) {
            return false;
        }
        int totalFields = 0;
        for (JsonElement line : el.getAsJsonArray()) {
            if (line == null || !line.isJsonArray()) {
                return false;
            }
            if (line.getAsJsonArray().size() > MAX_FIELDS_PER_ROW) {
                return false;
            }
            for (JsonElement field : line.getAsJsonArray()) {
                if (field == null || !field.isJsonPrimitive()
                        || !field.getAsJsonPrimitive().isString()
                        || field.getAsString().isBlank()
                        || field.getAsString().length() > MAX_FIELD_LENGTH
                        || !DESCRIPTION_FIELDS.contains(
                                field.getAsString().toLowerCase(java.util.Locale.ROOT))) {
                    return false;
                }
                totalFields++;
                if (totalFields > MAX_TOTAL_FIELDS) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Validates every member required by the current backend model. A partial
     * object is never safe to write back because the lore import replaces the
     * complete settings object.
     */
    public boolean isCompleteSnapshot() {
        for (String member : REQUIRED_MEMBERS) {
            if (!hasSingleMember(member)) {
                return false;
            }
        }
        if (!hasFields()) {
            return false;
        }
        for (String name : BOOLEAN_MEMBERS) {
            if (!isBoolean(name)) {
                return false;
            }
        }
        if (!isIntegralWithin(
                member("MinProfitForHighlight"),
                BigDecimal.valueOf(Long.MIN_VALUE),
                BigDecimal.valueOf(Long.MAX_VALUE))) {
            return false;
        }
        for (String name : BYTE_MEMBERS) {
            if (!isIntegralWithin(
                    member(name),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(255))) {
                return false;
            }
        }
        for (String name : NULLABLE_STRING_MEMBERS) {
            if (!isNullableString(member(name))) {
                return false;
            }
        }
        for (String name : NULLABLE_STRING_ARRAY_MEMBERS) {
            if (!isNullableStringArray(member(name))) {
                return false;
            }
        }
        return isValidHighlightInfo(member("HighlightInfo"));
    }

    private boolean isBoolean(String name) {
        JsonElement value = member(name);
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isBoolean();
    }

    private static boolean isIntegralWithin(
            JsonElement element,
            BigDecimal minimum,
            BigDecimal maximum) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            BigDecimal value = element.getAsBigDecimal();
            return value.stripTrailingZeros().scale() <= 0
                    && value.compareTo(minimum) >= 0
                    && value.compareTo(maximum) <= 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isNullableString(JsonElement element) {
        return element != null
                && (element.isJsonNull()
                || element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString());
    }

    private static boolean isNullableStringArray(JsonElement element) {
        if (element == null) {
            return false;
        }
        if (element.isJsonNull()) {
            return true;
        }
        if (!element.isJsonArray()) {
            return false;
        }
        for (JsonElement value : element.getAsJsonArray()) {
            if (value == null
                    || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidHighlightInfo(JsonElement element) {
        if (element == null) {
            return false;
        }
        if (element.isJsonNull()) {
            return true;
        }
        if (!element.isJsonObject()) {
            return false;
        }
        JsonObject info = element.getAsJsonObject();
        for (String member : List.of("Position", "HexColor", "SlotId", "Chestname")) {
            if (!hasSingleMember(info, member)) {
                return false;
            }
        }
        JsonElement position = member(info, "Position");
        return position != null
                && (position.isJsonNull() || isValidBlockPosition(position))
                && isNullableString(member(info, "HexColor"))
                && isIntegralWithin(
                        member(info, "SlotId"),
                        BigDecimal.valueOf(Integer.MIN_VALUE),
                        BigDecimal.valueOf(Integer.MAX_VALUE))
                && isNullableString(member(info, "Chestname"));
    }

    private static boolean isValidBlockPosition(JsonElement element) {
        if (!element.isJsonObject()) {
            return false;
        }
        JsonObject position = element.getAsJsonObject();
        for (String coordinate : List.of("X", "Y", "Z")) {
            if (!hasSingleMember(position, coordinate)
                    || !isFiniteNumber(member(position, coordinate))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFiniteNumber(JsonElement element) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return false;
        }
        try {
            return Double.isFinite(element.getAsDouble());
        } catch (RuntimeException exception) {
            return false;
        }
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
        return actualKey(root, name);
    }

    private static String actualKey(JsonObject object, String name) {
        if (object.has(name)) {
            return name;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String k : object.keySet()) {
            if (k.toLowerCase(java.util.Locale.ROOT).equals(lower)) {
                return k;
            }
        }
        return null;
    }

    private JsonElement member(String name) {
        String key = actualKey(name);
        return key == null ? null : root.get(key);
    }

    private boolean hasSingleMember(String name) {
        return hasSingleMember(root, name);
    }

    private static JsonElement member(JsonObject object, String name) {
        String key = actualKey(object, name);
        return key == null ? null : object.get(key);
    }

    private static boolean hasSingleMember(JsonObject object, String name) {
        int count = 0;
        for (String key : object.keySet()) {
            if (key.equalsIgnoreCase(name) && ++count > 1) {
                return false;
            }
        }
        return count == 1;
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
