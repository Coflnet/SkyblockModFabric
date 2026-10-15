package com.coflnet.core;

import java.util.Objects;

/** A highlight recorded for one menu must not follow an identical item id into another menu. */
public final class HighlightStaleness {
    private HighlightStaleness() {}

    public record Key(String screenTitle, String itemName) {}

    public static boolean isStale(Key recorded, String currentTitle, String currentItemName) {
        // No record means the highlight was never confirmed for this menu: treat as stale.
        if (recorded == null) return true;
        return !Objects.equals(recorded.screenTitle(), currentTitle)
                || !Objects.equals(recorded.itemName(), currentItemName);
    }
}
