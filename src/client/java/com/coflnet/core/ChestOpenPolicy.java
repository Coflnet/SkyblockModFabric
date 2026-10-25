package com.coflnet.core;

/**
 * Decides whether a freshly opened container may be uploaded right away. Hypixel sends the slot
 * contents after the open packet, so a chest part that is still entirely empty is deferred to the
 * {@link ChestChangeDebouncer}, which uploads once after the quiet period with whatever arrived.
 */
public final class ChestOpenPolicy {
    private ChestOpenPolicy() {
    }

    /** True when every slot before the trailing player-inventory slots is empty (also for zero chest slots). */
    public static boolean isChestPartEmpty(boolean[] slotEmpty) {
        int chestSlots = slotEmpty.length - ChestChangeDebouncer.PLAYER_INVENTORY_SLOTS;
        for (int i = 0; i < chestSlots; i++) {
            if (!slotEmpty[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param slotEmpty emptiness of every menu slot in order
     * @param playerInventoryScreen the player's own inventory screen (no chest part)
     * @param tradeMenu a trade window (priced by its own logic)
     * @return true to upload now, false to defer to the debouncer
     */
    public static boolean shouldUploadOnOpen(boolean[] slotEmpty, boolean playerInventoryScreen, boolean tradeMenu) {
        if (playerInventoryScreen || tradeMenu) {
            return true;
        }
        return !isChestPartEmpty(slotEmpty);
    }
}
