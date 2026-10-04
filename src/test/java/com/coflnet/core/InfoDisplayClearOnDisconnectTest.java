package com.coflnet.core;

import com.coflnet.gui.hud.InfoDisplayManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InfoDisplayClearOnDisconnectTest {
    @Test void clearAllEmptiesEveryDisplay() {
        for (int id = 1; id <= InfoDisplayManager.DISPLAY_COUNT; id++) {
            InfoDisplayManager.apply(new InfoDisplayPayload(id, "t", List.of(
                    new InfoDisplayPayload.InfoDisplayLine("line", "hover", null)), 0, false));
            assertNotNull(InfoDisplayManager.snapshot(id));
        }
        InfoDisplayManager.clearAll();
        for (int id = 1; id <= InfoDisplayManager.DISPLAY_COUNT; id++) {
            assertNull(InfoDisplayManager.snapshot(id));
        }
    }
}
