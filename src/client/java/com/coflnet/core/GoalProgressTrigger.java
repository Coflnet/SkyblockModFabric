package com.coflnet.core;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decides when a tracked goal should be re-evaluated by the backend without waiting for the player
 * to open a screen: a confirming chat line, or a change of the main inventory while a container is
 * open. Pure logic with an injected clock so it is unit-testable.
 */
public final class GoalProgressTrigger {
    /** Chat lines that confirm a goal step; matched only at the start of the (unformatted) line. */
    private static final List<String> PREFIXES = List.of(
            "You purchased ",
            "You claimed ",
            "You Supercrafted ",
            "[Bazaar] Bought ",
            "[Bazaar] Claimed ",
            "[Bazaar] Sold ",
            "[Bazaar] Buy Order Setup!",
            "[Bazaar] Sell Offer Setup!",
            "[Bazaar] Cancelled!",
            "You cancelled your auction for ",
            "BIN Auction started for ",
            "Auction started for ");

    /** Wait after the last trigger so the item has reached the inventory; also collapses bursts. */
    public static final long DEFAULT_DELAY_MS = 400;
    /** At most one refresh per this interval. */
    public static final long DEFAULT_MIN_INTERVAL_MS = 2000;
    /** How often the inventory fingerprint is compared while a container is open. */
    public static final long FINGERPRINT_CHECK_INTERVAL_MS = 300;
    /** Slots 0-35 of the player inventory (hotbar + main). */
    public static final int MAIN_INVENTORY_SLOTS = 36;

    private static final java.util.regex.Pattern FILLED_ORDER = java.util.regex.Pattern.compile(
            "^\\[Bazaar\\] Your (?:Buy Order|Sell Offer) for [\\d,]+x .+ was filled!$");

    private final long delayMs;
    private final long minIntervalMs;
    private boolean pending;
    private boolean forcedRefresh;
    private long lastTriggerAt;
    private boolean hasRefreshed;
    private long lastRefreshAt;

    /** After a progress chat line the inventory is watched for this long even without an open screen. */
    public static final long WATCH_WINDOW_MS = 5000;
    /** Menu key used by {@link #observeInventory} when no container screen is open. */
    public static final Object NO_SCREEN = new Object();

    private long watchUntil = Long.MIN_VALUE;
    private long lastFingerprintCheckAt = Long.MIN_VALUE;
    private Object baselineMenu;
    private long baselineFingerprint;

    public GoalProgressTrigger() {
        this(DEFAULT_DELAY_MS, DEFAULT_MIN_INTERVAL_MS);
    }

    public GoalProgressTrigger(long delayMs, long minIntervalMs) {
        this.delayMs = delayMs;
        this.minIntervalMs = minIntervalMs;
    }

    public static boolean isProgressMessage(String chatLine) {
        if (chatLine == null) return false;
        String text = FormattingCodes.strip(chatLine);
        for (String prefix : PREFIXES) {
            if (text.startsWith(prefix)) return true;
        }
        return FILLED_ORDER.matcher(text).matches();
    }

    /** Record that a refresh is wanted; bursts collapse into one. */
    public synchronized void trigger(long now) {
        pending = true;
        lastTriggerAt = now;
    }

    /** A backend login or confirmed action requires a refresh even with identical inventory. */
    public synchronized void forceRefresh(long now) {
        forcedRefresh = true;
        trigger(now);
    }

    public synchronized boolean hasForcedRefresh() {
        return forcedRefresh;
    }

    public synchronized boolean consumeForcedRefresh() {
        boolean forced = forcedRefresh;
        forcedRefresh = false;
        return forced;
    }

    /** Start (or extend) the window in which inventory changes are watched without an open screen. */
    public synchronized void openWatch(long now) {
        watchUntil = Math.max(watchUntil, now + WATCH_WINDOW_MS);
    }

    public synchronized boolean isWatching(long now) {
        return watchUntil != Long.MIN_VALUE && now < watchUntil;
    }

    /** @return true exactly once per due refresh (the caller must then perform it) */
    public synchronized boolean pollDue(long now) {
        if (!pending) return false;
        long due = lastTriggerAt + delayMs;
        if (hasRefreshed) due = Math.max(due, lastRefreshAt + minIntervalMs);
        if (now < due) return false;
        pending = false;
        hasRefreshed = true;
        lastRefreshAt = now;
        return true;
    }

    /** Drop pending work, e.g. when the goal is no longer tracked. */
    public synchronized void reset() {
        pending = false;
        forcedRefresh = false;
        hasRefreshed = false;
        baselineMenu = null;
        watchUntil = Long.MIN_VALUE;
        lastFingerprintCheckAt = Long.MIN_VALUE;
    }

    /** Low-rate gate for the fingerprint comparison. */
    public synchronized boolean shouldCheckFingerprint(long now) {
        if (lastFingerprintCheckAt != Long.MIN_VALUE && now - lastFingerprintCheckAt < FINGERPRINT_CHECK_INTERVAL_MS) {
            return false;
        }
        lastFingerprintCheckAt = now;
        return true;
    }

    /**
     * Compare the main inventory against the baseline of the open menu. The first observation of a
     * menu only sets the baseline (the screen-open upload covers it).
     *
     * @return true when the inventory content changed since the previous observation of that menu
     */
    public synchronized boolean observeInventory(Object menu, long fingerprint) {
        if (baselineMenu != menu) {
            baselineMenu = menu;
            baselineFingerprint = fingerprint;
            return false;
        }
        if (baselineFingerprint == fingerprint) return false;
        baselineFingerprint = fingerprint;
        return true;
    }

    /** No container is open any more. */
    public synchronized void menuClosed() {
        baselineMenu = null;
    }

    /**
     * Order-independent fingerprint of item identity and total count; empty slots (null/empty key)
     * are ignored, so moving or splitting stacks does not change it but gaining or losing items does.
     */
    public static long fingerprint(String[] keys, int[] counts) {
        Map<String, Long> totals = new TreeMap<>();
        for (int i = 0; i < keys.length && i < counts.length; i++) {
            if (keys[i] == null || keys[i].isEmpty() || counts[i] <= 0) continue;
            totals.merge(keys[i], (long) counts[i], Long::sum);
        }
        long hash = 1125899906842597L;
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + Long.hashCode(entry.getValue());
            hash = 31 * hash + entry.getKey().length();
        }
        return hash;
    }
}
