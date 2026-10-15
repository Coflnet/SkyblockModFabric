package com.coflnet.gui;

import com.coflnet.core.HighlightStaleness;
import com.coflnet.core.HighlightStaleness.Key;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Remembers which menu and item name a highlighted item id was confirmed for. */
public final class HighlightRegistry {
    private static final Map<String, Key> KEYS = new ConcurrentHashMap<>();

    private HighlightRegistry() {}

    public static void record(String itemId, String screenTitle, String itemName) {
        KEYS.put(itemId, new Key(screenTitle, itemName));
    }

    public static boolean isStale(String itemId, String currentTitle, String currentItemName) {
        return HighlightStaleness.isStale(KEYS.get(itemId), currentTitle, currentItemName);
    }

    public static void clear() {
        KEYS.clear();
    }
}
