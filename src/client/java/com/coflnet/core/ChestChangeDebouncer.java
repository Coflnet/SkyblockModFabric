package com.coflnet.core;

/**
 * Decides when a change to the open chest GUI needs a description reload and coalesces bursts of
 * slot packets into a single reload (trailing debounce with a maximum wait so a constantly
 * changing menu still refreshes). Pure logic, no Minecraft types, so it is unit-testable.
 *
 * <p>Usage: call {@link #markChanged} on every relevant slot change; when it returns {@code true}
 * start one waiter that loops on {@link #poll} (sleep for the returned delay while it is positive,
 * reload when it returns 0, stop when it returns -1).
 */
public final class ChestChangeDebouncer {
    /** Slots of the player inventory (27 main + 9 hotbar) that trail the chest part of a menu. */
    public static final int PLAYER_INVENTORY_SLOTS = 36;

    private final long quietMs;
    private final long maxWaitMs;
    private boolean pending;
    private boolean waiterActive;
    private long firstChangeAt;
    private long lastChangeAt;

    public ChestChangeDebouncer(long quietMs, long maxWaitMs) {
        this.quietMs = quietMs;
        this.maxWaitMs = Math.max(quietMs, maxWaitMs);
    }

    /** True when {@code slot} belongs to the chest part of the menu, not the player's own inventory. */
    public static boolean isChestSlot(int slot, int totalSlots) {
        return slot >= 0 && slot < totalSlots - PLAYER_INVENTORY_SLOTS;
    }

    /**
     * A reload is needed when a chest slot's content really changed (including being cleared) in a
     * menu that does not do its own pricing (trades).
     */
    public static boolean needsReload(int slot, int totalSlots, boolean contentChanged, boolean tradeMenu) {
        return contentChanged && !tradeMenu && isChestSlot(slot, totalSlots);
    }

    /** @return true if the caller must start a waiter thread (none is active yet) */
    public synchronized boolean markChanged(long now) {
        if (!pending) {
            pending = true;
            firstChangeAt = now;
        }
        lastChangeAt = now;
        boolean startWaiter = !waiterActive;
        waiterActive = true;
        return startWaiter;
    }

    /** @return ms still to wait (&gt;0), 0 when the reload is due (consumed), -1 when nothing is pending */
    public synchronized long poll(long now) {
        if (!pending) {
            waiterActive = false;
            return -1;
        }
        long due = Math.min(lastChangeAt + quietMs, firstChangeAt + maxWaitMs);
        if (now >= due) {
            pending = false;
            waiterActive = false;
            return 0;
        }
        return due - now;
    }

    /** Drop pending work, e.g. when another menu became active. A running waiter exits on its next poll. */
    public synchronized void reset() {
        pending = false;
    }
}
