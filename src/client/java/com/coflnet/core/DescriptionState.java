package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;

/** Displayed description state. Apply, reset and callback registration belong to the client thread. */
public final class DescriptionState {
    private static DescriptionHandler.DescModification[] panel = new DescriptionHandler.DescModification[0];
    private static DescriptionHandler.DescriptionRefreshCallback callback;

    private DescriptionState() {}

    public static DescriptionHandler.DescModification[] panel() { return panel; }

    public static void setRefreshCallback(DescriptionHandler.DescriptionRefreshCallback listener) { callback = listener; }

    public static void apply(DescriptionRequest request, DescriptionHandler.DescModification[][] response) {
        for (int slot = 0; slot < request.itemIds().length; slot++) {
            String id = request.itemIds()[slot];
            if (!id.isEmpty()) DescriptionHandler.tooltipItemIdMap.put(id, response[slot]);
        }
        panel = response.length > request.itemIds().length ? response[response.length - 1]
                : new DescriptionHandler.DescModification[0];
        if (panel == null) panel = new DescriptionHandler.DescModification[0];
        if (callback != null) callback.onDescriptionRefresh(request.itemIds(), request.title());
    }

    public static void reset() {
        DescriptionHandler.emptyTooltipData();
        panel = new DescriptionHandler.DescModification[0];
        if (callback != null) callback.onDescriptionRefresh(new String[0], "");
    }
}
