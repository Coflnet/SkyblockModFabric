package com.coflnet.skyblock.testserver;

import java.util.List;

public final class ScenarioExpectations {
    public static final String PURSE_LINE = "Purse: 12,345";
    public static final String LOCATION_ENTRY = "hub";
    public static final String LOCATION_PREFIX = " ⏣ ";
    public static final String LOCATION_LINE = LOCATION_PREFIX + LOCATION_ENTRY;
    public static final List<String> MENU_PACKET_ORDER = List.of(
            "menu.slot-update-newer", "menu.slot-update-older", "menu.close", "menu.reopen");
    public static final List<String> WORLD_PACKET_ORDER = List.of(
            "entity.metadata-update", "chunk.unload", "chunk.reload");

    private ScenarioExpectations() {
    }
}
