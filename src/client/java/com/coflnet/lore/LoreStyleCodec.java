package com.coflnet.lore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Locale;

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

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private LoreStyleCodec() {
    }

    /**
     * serialises the modules templates into the blob. skips any module whose
     * template is blank or equal to the segments stock default so only genuine
     * customisations travel. returns null when nothing is customised clears
     * {@code customFormat}).
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
            String key = seg != null ? seg.key : m.match;
            String template = m.template;
            // only sync a genuine customisation blank or stock default is skipped .
            String stock = seg != null ? seg.defaultTemplate : null;
            if (template.isBlank()) {
                // a blank template means hide this field that is a customisation.
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

    /**
     * applies a styling blob onto the given modules in place overwriting the
     * template of any module whose segment key appears in the blob. modules not
     * mentioned keep their current default template. tolerates a malformed blob
     * by leaving the modules untouched.
     */
    public static void applyToModules(String customFormat, List<LoreModule> modules) {
        if (customFormat == null || customFormat.isBlank() || modules == null) {
            return;
        }
        JsonObject obj;
        try {
            JsonElement el = JsonParser.parseString(customFormat);
            if (!el.isJsonObject()) {
                return;   // not our blob some other client wrote a plain string 
            }
            obj = el.getAsJsonObject();
        } catch (Exception e) {
            return;   // opaque non json customformat from another client ignore
        }
        for (LoreModule m : modules) {
            if (m == null || m.match == null) {
                continue;
            }
            LoreSegment seg = LoreSegment.byKey(m.match);
            String key = seg != null ? seg.key : m.match;
            JsonElement val = findIgnoreCase(obj, key);
            if (val != null && val.isJsonPrimitive()) {
                m.template = val.getAsString();
            }
        }
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
}
