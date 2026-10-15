package com.coflnet.core;

import CoflCore.classes.Position;
import CoflCore.handlers.DescriptionHandler;

/** Captured request data, independent of the screen that supplied it. */
public record DescriptionRequest(String title, String[] itemIds, String inventoryNbt,
                                 String username, Position position) {
    public void load() {
        DescriptionHandler.loadDescriptionForInventory(itemIds, title, inventoryNbt, username, position);
    }

    /** Preserve positional prices even when distinct items have the same tooltip cache ID. */
    public DescriptionHandler.DescModification[][] loadBySlot() {
        String prefix = "trade:" + java.util.UUID.randomUUID() + ":";
        String[] slotIds = new String[itemIds.length];
        java.util.Arrays.setAll(slotIds, slot -> prefix + slot);
        try {
            DescriptionHandler.loadDescriptionForInventory(slotIds, title, inventoryNbt, username, position);
            var result = new DescriptionHandler.DescModification[itemIds.length][];
            for (int slot = 0; slot < itemIds.length; slot++) {
                result[slot] = DescriptionHandler.getTooltipData(slotIds[slot]);
            }
            return result;
        } finally {
            for (String id : slotIds) DescriptionHandler.tooltipItemIdMap.remove(id);
        }
    }

    /** Never wait for the network on the caller, including when no throttle delay is needed. */
    public void submit(long delayMs, Runnable onLoaded) {
        submit(delayMs, onLoaded, () -> {});
    }

    public void submit(long delayMs, Runnable onLoaded, Runnable onFailed) {
        Thread.ofVirtual().name("CoflSky-Descriptions").start(() -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                load();
                onLoaded.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                System.err.println("[descriptions] Failed to load " + title + "; refresh can be retried: " + e);
                onFailed.run();
            }
        });
    }
}
