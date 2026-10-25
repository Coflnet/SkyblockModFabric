package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;
import CoflCore.network.WSClient;
import com.coflnet.models.TextElement;
import com.google.gson.JsonParseException;

/** Displayed description state. Apply, reset and callback registration belong to the client thread. */
public final class DescriptionState {
    private static final DescriptionHandler.DescModification[] EMPTY_PANEL = new DescriptionHandler.DescModification[0];
    private static final BoundedLruMap<String, DescriptionHandler.DescModification[]> panels = new BoundedLruMap<>(64);
    private static DescriptionHandler.DescModification[] panel = EMPTY_PANEL;
    private static DescriptionHandler.DescriptionRefreshCallback callback;
    private static Object currentScreen;

    private DescriptionState() {}

    public static DescriptionHandler.DescModification[] panel() { return panel; }

    /** Restore only this title's informational panel; resizing keeps the current fresh response. */
    public static DescriptionHandler.DescModification[] openPanel(String title, Object screen) {
        if (currentScreen != screen) {
            currentScreen = screen;
            panel = panels.getOrDefault(title, EMPTY_PANEL);
        }
        return panel;
    }

    /** Keep the live suggestion for an opening sign, but require a cache lookup when returning. */
    public static void closePanel(Object screen) {
        if (currentScreen == screen) currentScreen = null;
    }

    public static void setRefreshCallback(DescriptionHandler.DescriptionRefreshCallback listener) { callback = listener; }

    public static void apply(DescriptionRequest request, DescriptionHandler.DescModification[][] response) {
        for (int slot = 0; slot < request.itemIds().length; slot++) {
            String id = request.itemIds()[slot];
            if (!id.isEmpty()) DescriptionHandler.tooltipItemIdMap.put(id, response[slot]);
        }
        panel = response.length > request.itemIds().length ? response[response.length - 1]
                : EMPTY_PANEL;
        if (panel == null) panel = EMPTY_PANEL;
        if (panel.length > 0 && canCache(panel)) panels.put(request.title(), panel);
        else panels.remove(request.title());
        if (callback != null) callback.onDescriptionRefresh(request.itemIds(), request.title());
    }

    private static boolean canCache(DescriptionHandler.DescModification[] lines) {
        for (var line : lines) {
            if ("SUGGEST".equals(line.type)) return false;
            if (line.value == null || !line.value.trim().startsWith("[")) continue;
            try {
                for (var element : WSClient.gson.fromJson(line.value, TextElement[].class)) {
                    if (element == null || element.onClick == null) continue;
                    String action = element.onClick.trim();
                    if (action.startsWith("fillsign:") || action.startsWith("suggest:")
                            || action.startsWith("/cofl bzorder")) return false;
                }
            } catch (JsonParseException invalid) {
                return false;
            }
        }
        return true;
    }

    public static void reset() {
        DescriptionHandler.emptyTooltipData();
        panels.clear();
        panel = EMPTY_PANEL;
        currentScreen = null;
        if (callback != null) callback.onDescriptionRefresh(new String[0], "");
    }
}
