package com.coflnet.core;

import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/** Only explicitly Bazaar-tradeable products are eligible for the graph hotkey. */
public record BazaarGraphCatalog(Map<String, BazaarGraphTarget> ids, Map<String, BazaarGraphTarget> names) {
    public static BazaarGraphCatalog parse(String json) {
        var ids = new HashMap<String, BazaarGraphTarget>();
        var names = new HashMap<String, BazaarGraphTarget>();
        var ambiguous = new HashSet<String>();
        for (var row : JsonParser.parseString(json).getAsJsonArray()) {
            try {
                var item = row.getAsJsonObject();
                if (!item.has("flags") || item.get("flags").isJsonNull()) continue;
                var flags = item.get("flags");
                boolean bazaar = flags.getAsJsonPrimitive().isNumber() ? (flags.getAsInt() & 1) != 0
                        : java.util.Arrays.asList(flags.getAsString().split(",\\s*")).contains("BAZAAR");
                if (!bazaar || !item.has("tag") || !item.has("name") || item.get("name").isJsonNull()) continue;
                String id = item.get("tag").getAsString();
                if (!id.matches("[A-Za-z0-9_:.-]{1,150}")) continue;
                var target = new BazaarGraphTarget(id, BazaarChart.clean(item.get("name").getAsString()));
                ids.put(id, target);
                alias(names, ambiguous, target.query(), target);
                if (id.startsWith("ENCHANTMENT_")) {
                    int last = id.lastIndexOf('_');
                    try {
                        var book = BazaarGraphTarget.from("ENCHANTED_BOOK", "Enchanted Book",
                                Map.of(id.substring(12, last).toLowerCase(Locale.ROOT), Integer.parseInt(id.substring(last + 1))));
                        if (book != null) alias(names, ambiguous, book.query(), target);
                    } catch (RuntimeException ignored) { }
                }
            } catch (RuntimeException ignored) {
                // A malformed entry must not disable graphs for the remaining products.
            }
        }
        ambiguous.forEach(names::remove);
        return new BazaarGraphCatalog(Map.copyOf(ids), Map.copyOf(names));
    }

    private static void alias(Map<String, BazaarGraphTarget> names, HashSet<String> ambiguous, String name, BazaarGraphTarget target) {
        String key = name.toLowerCase(Locale.ROOT);
        var previous = names.putIfAbsent(key, target);
        if (previous != null && !previous.id().equals(target.id())) ambiguous.add(key);
    }

    public BazaarGraphTarget resolve(BazaarGraphTarget candidate) {
        if (candidate == null) return null;
        var found = candidate.id().isEmpty() ? names.get(candidate.query().toLowerCase(Locale.ROOT)) : ids.get(candidate.id());
        return found == null ? null : new BazaarGraphTarget(found.id(), candidate.query());
    }
}
