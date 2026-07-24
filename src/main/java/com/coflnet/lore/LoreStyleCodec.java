package com.coflnet.lore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * encodes the users per field styling templates into the single opaque
 * {@code customFormat} string the backend stores and hands back untouched, and
 * decodes it on the way in.
 *
 * this is the one string styling sync approach agreed with the skycofl dev
 * the backend never parses this blob (it keeps treating {@code fields} as the
 * authoritative field list for its per item selection it only stores and
 * returns it so the field detection path stays clean while a users styling
 * follows them across instances through the normal settings sync.
 *
 * The blob is a compact JSON object mapping a segment KEY (e.g. {@code "LBIN"})
 * to that field's template string with its {@code {tokens}} intact
 * (e.g. {@code "&l&cLowest &eBin: &r{lbin}"}). Only non-default, non-blank
 * templates are written so an unstyled install produces an empty blob and a
 * later mod update that changes a default is not frozen out by a stale copy.
 */
public final class LoreStyleCodec {

    public static final int MAX_TEMPLATE_LENGTH = 200;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LoreStyleCodec() {
    }

    /**
     * serialises the modules templates into the blob. a template equal to the
     * segments stock default is skipped since the mod already renders that look. a
     * blank template is written as an empty string on purpose it means show the stock
     * backend look and must travel so that choice propagates across instances rather
     * than reading as an absent key. returns null when nothing is left to write which
     * clears {@code customFormat}.
     */
    public static String fromModules(List<LoreModule> modules) {
        if (modules == null || modules.isEmpty()) {
            return null;
        }
        JsonObject obj = new JsonObject();
        for (LoreModule m : modules) {
            if (m == null || m.match == null || m.match.isBlank() || m.template == null) {
                continue;
            }
            LoreSegment seg = LoreSegment.byKey(m.match);
            if (seg == null) {
                continue;
            }
            String key = seg.key;
            String template = m.template;
            if (template.length() > MAX_TEMPLATE_LENGTH) {
                throw new IllegalArgumentException("lore template exceeded the size limit");
            }
            // only sync a genuine customisation blank or stock default is skipped .
            String stock = seg != null ? seg.defaultTemplate : null;
            if (template.isBlank()) {
                // a blank template shows the stock backend look still a customisation
                // vs the mods default so sync it as an empty string.
                obj.addProperty(key, "");
                continue;
            }
            if (stock != null && stock.equals(template)) {
                continue;
            }
            obj.addProperty(key, template);
        }
        return obj.size() == 0 ? null : GSON.toJson(obj);
    }

    public static String mergeInto(String existing, List<LoreModule> modules) {
        JsonObject merged = new JsonObject();
        if (existing != null && !existing.isBlank()) {
            JsonObject parsed = BoundedJson.parseObject(
                    existing,
                    LoreSettingsPayload.MAX_PAYLOAD_LENGTH);
            if (parsed == null) {
                throw new IllegalArgumentException("existing custom format is not a compatible json object");
            }
            merged = parsed;
        }
        for (LoreSegment segment : LoreSegment.ALL) {
            removeIgnoreCase(merged, segment.key);
        }
        String owned = fromModules(modules);
        if (owned != null) {
            JsonObject ownedObject = JsonParser.parseString(owned).getAsJsonObject();
            for (String key : ownedObject.keySet()) {
                merged.add(key, ownedObject.get(key));
            }
        }
        return merged.size() == 0 ? null : GSON.toJson(merged);
    }

    /**
     * applies a styling blob onto the given modules in place overwriting the
     * template of any module whose segment key appears in the blob. modules not
     * mentioned keep their current default template. tolerates a malformed blob
     * by leaving the modules untouched.
     */
    public static void applyToModules(String customFormat, List<LoreModule> modules) {
        if (modules == null) {
            return;
        }
        // a null or blank blob means the source instance is entirely stock default so
        // it is authoritative reset every restylable module to default via the empty
        // object below.
        JsonObject obj = new JsonObject();
        boolean authoritative = true;
        if (customFormat != null && !customFormat.isBlank()) {
            JsonObject parsed = BoundedJson.parseObject(
                    customFormat,
                    LoreSettingsPayload.MAX_PAYLOAD_LENGTH);
            if (parsed == null || !hasValidOwnedValues(parsed)) {
                return;
            }
            obj = parsed;
            // only reset absent keys to default when this looks like our blob at least
            // one key maps to a restylable segment . a foreign object we do not
            // recognise is applied for any matching keys but never resets our fields.
            authoritative = blobIsOurs(obj);
        }
        for (LoreModule m : modules) {
            if (m == null || m.match == null) {
                continue;
            }
            LoreSegment seg = LoreSegment.byKey(m.match);
            if (seg == null) {
                continue;
            }
            String key = seg.key;
            JsonElement val = findIgnoreCase(obj, key);
            if (val != null && val.isJsonPrimitive()) {
                m.template = val.getAsString();
            } else if (seg != null && authoritative) {
                // the blob is authoritative for every restylable field a key absent
                // from it means default so a reset or un hide made on another instance
                // propagates here instead of leaving a stale customisation in place.
                m.template = seg.defaultTemplate;
            }
        }
    }

    /** true when the blob is empty or carries at least one restylable segment key. */
    private static boolean blobIsOurs(JsonObject obj) {
        if (obj.size() == 0) {
            return true;
        }
        for (String k : obj.keySet()) {
            if (LoreSegment.byKey(k) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasValidOwnedValues(JsonObject obj) {
        Set<String> seen = new HashSet<>();
        for (String key : obj.keySet()) {
            LoreSegment segment = LoreSegment.byKey(key);
            if (segment == null) {
                continue;
            }
            if (!seen.add(segment.key)
                    || !obj.get(key).isJsonPrimitive()
                    || !obj.get(key).getAsJsonPrimitive().isString()
                    || obj.get(key).getAsString().length() > MAX_TEMPLATE_LENGTH) {
                return false;
            }
        }
        return true;
    }

    /** case insensitive member lookup so key casing differences do not lose styling. */
    private static JsonElement findIgnoreCase(JsonObject obj, String key) {
        if (obj.has(key)) {
            return obj.get(key);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (String member : obj.keySet()) {
            if (member.toLowerCase(Locale.ROOT).equals(lower)) {
                return obj.get(member);
            }
        }
        return null;
    }

    private static void removeIgnoreCase(JsonObject obj, String key) {
        List<String> matches = new ArrayList<>();
        for (String member : obj.keySet()) {
            if (member.equalsIgnoreCase(key)) {
                matches.add(member);
            }
        }
        for (String match : matches) {
            obj.remove(match);
        }
    }
}
