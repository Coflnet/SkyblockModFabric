package com.coflnet.lore;

import com.coflnet.config.LoreManager;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    /**
     * returns the lore lines to show for one item or null to keep the stock
     * backend lines entirely.
     *
     * @param backendValues the raw backend APPEND strings for this item, colour
     *  codes intact in backend order
     * @param itemId        the stable skyblock item type tag for the blacklist check
     *  so blacklisting one hyperion hides the lore on every hyperion not one stack
     * @param displayName   the item's clean display name used to look up the price
     *  you paid recorded from ah purchase chat when the backend sends no paid line
     */
    public static List<String> render(List<String> backendValues, String itemId, String displayName) {
        try {
            return renderInner(backendValues, itemId, displayName);
        } catch (RuntimeException exception) {
            if (FAILURE_LOGGED.compareAndSet(false, true)) {
                System.out.println("[Lore] render failed, keeping stock lore: " + exception);
            }
            return null;
        }
    }

    private static List<String> renderInner(List<String> backendValues, String itemId, String displayName) {
        if (LoreManager.isItemBlacklisted(itemId)) {
            return null;
        }
        if (backendValues == null) {
            backendValues = new ArrayList<>();
        }

        // map of segment key module template non blank only.
        Map<String, String> templates = new HashMap<>();
        for (LoreModule m : LoreManager.getModules()) {
            if (m == null || m.match == null || m.match.isBlank()
                    || m.template == null || m.template.isBlank()) {
                continue;
            }
            // skip a module still at its stock default the user has not customised it so
            // leave the backend line exactly as sent estimate markers abbreviations
            // colours and all . only genuinely customised fields are ever restyled.
            LoreSegment seg = LoreSegment.byKey(m.match);
            if (seg != null && seg.defaultTemplate.equals(m.template)) {
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
            stripped.add(v == null ? null : ChatFormatting.stripFormatting(v));
        }
        LoreData data = LoreParser.parse(stripped);
        // fall back to what you paid recorded from the ah purchase chat when the
        // backend did not supply a paid line so the purchased paid tokens work.
        if (data.purchasedFor == null && displayName != null) {
            Long paid = LoreManager.purchasePrice(displayName);
            if (paid != null) {
                data.purchasedFor = (double) (long) paid;
            }
        }

        boolean changedAny = false;
        List<String> out = new ArrayList<>(backendValues.size());

        for (String backendLine : backendValues) {
            if (backendLine == null) {
                out.add(null);   // defensive the caller filters nulls but never NPE here.
                continue;
            }
            String rendered = renderLine(backendLine, templates, data);
            if (!rendered.equals(backendLine)) {
                changedAny = true;
            }
            out.add(rendered);
        }

        return changedAny ? out : null;   // no change let caller keep stock
    }

    public static String renderLine(String backendLine, Map<String, String> templates, LoreData data) {
        List<Replacement> replacements = new ArrayList<>();
        if (backendLine == null || templates == null || templates.isEmpty()) {
            return backendLine;
        }
        for (LoreSegment seg : LoreSegment.ALL) {
            String template = templates.get(seg.key.toUpperCase(java.util.Locale.ROOT));
            if (template == null) {
                continue;
            }
            Matcher matcher = seg.pattern.matcher(backendLine);
            if (!matcher.find()) {
                continue;
            }
            String fragment = LoreTemplate.render(template, data);
            if (fragment == null || fragment.isBlank()) {
                continue;
            }
            String matched = matcher.group();
            if (fragment.equals(matched)) {
                continue;
            }
            replacements.add(new Replacement(matcher.start(), matcher.end(), fragment));
        }
        if (replacements.isEmpty()) {
            return backendLine;
        }
        replacements.sort((left, right) -> Integer.compare(right.start, left.start));
        StringBuilder line = new StringBuilder(backendLine);
        for (Replacement replacement : replacements) {
            line.replace(replacement.start, replacement.end, replacement.text);
        }
        return line.toString();
    }

    private record Replacement(int start, int end, String text) {
    }
}
