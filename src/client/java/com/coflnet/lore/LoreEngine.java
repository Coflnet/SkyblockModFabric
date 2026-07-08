package com.coflnet.lore;

import com.coflnet.config.LoreManager;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * top level facade for the client side lore restyler.
 *  
 * the coflnet backend decides which fields appear and on which line and packs
 * several fields into one appended line e.g. med vol or
 * clean craft full craft cost . this engine never adds or removes backend
 * fields. for each backend line it performs per field segment substitution every
 * restyle module ({@link LoreModule#match} = a {@link LoreSegment} key) whose
 * pattern matches a segment of the line and whose template is non blank replaces
 * just that segment leaving the rest of the line other fields freeform
 * suffixes unknown text exactly as the backend sent it.
 *  
 * this is the fix for editing one field on a shared line hides the others the
 * old engine replaced the whole line from a single template so the unedited
 * fields on that line vanished. segment substitution edits each field
 * independently and is inherently passthrough safe.
 *  
 * the engine is skipped when the item id is blacklisted and any segment whose
 * template renders nothing is left untouched so it can never blank an item.
 */
public class LoreEngine {

    /**
     * returns the lore lines to show for one item or null to keep the stock
     * backend lines entirely.
     *
     * @param backendValues the raw backend APPEND strings for this item, colour
     *  codes intact in backend order
     * @param itemId        the SkyBlock item id (for the blacklist check)
     * @param displayName   the item's clean display name (unused now that the
     *  backend supplies price paid kept for signature compat 
     */
    public static List<String> render(List<String> backendValues, String itemId, String displayName) {
        if (LoreManager.isItemBlacklisted(itemId)) {
            return null;
        }
        if (backendValues == null) {
            backendValues = new ArrayList<>();
        }

        // map of segment key module template non blank only . a module
        // applies when its template is non blank clearing it disables that field.
        Map<String, String> templates = new HashMap<>();
        for (LoreModule m : LoreManager.getModules()) {
            if (m == null || m.match == null || m.match.isBlank()
                    || m.template == null || m.template.isBlank()) {
                continue;
            }
            templates.put(m.match.toUpperCase(java.util.Locale.ROOT), m.template);
        }
        if (templates.isEmpty()) {
            return null;   // nothing customised keep stock untouched
        }

        // parse the whole item once so any token resolves with full context.
        List<String> stripped = new ArrayList<>(backendValues.size());
        for (String v : backendValues) {
            stripped.add(ChatFormatting.stripFormatting(v));
        }
        LoreData data = LoreParser.parse(stripped);

        boolean changedAny = false;
        List<String> out = new ArrayList<>(backendValues.size());

        for (String backendLine : backendValues) {
            String line = backendLine;
            // try every segment against this line substitute the ones that match.
            for (LoreSegment seg : LoreSegment.ALL) {
                String template = templates.get(seg.key.toUpperCase(java.util.Locale.ROOT));
                if (template == null) {
                    continue;
                }
                Matcher matcher = seg.pattern.matcher(line);
                if (!matcher.find()) {
                    continue;
                }
                String fragment = LoreTemplate.render(template, data);
                if (fragment == null) {
                    continue;   // no data for this field leave the segment alone
                }
                // replace only this segment keep everything else on the line.
                line = matcher.replaceFirst(Matcher.quoteReplacement(fragment));
                changedAny = true;
            }
            out.add(line);
        }

        return changedAny ? out : null;   // no change let caller keep stock
    }
}
