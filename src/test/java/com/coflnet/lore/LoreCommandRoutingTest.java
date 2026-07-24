package com.coflnet.lore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreCommandRoutingTest {
    @Test
    void opensOnlyTheTwoExactGuiCommands() {
        assertTrue(LoreCommandRouting.opensGui("lore"));
        assertTrue(LoreCommandRouting.opensGui("  loregui  "));
        assertFalse(LoreCommandRouting.opensGui("lore enable"));
        assertFalse(LoreCommandRouting.opensGui("lore json"));
        assertFalse(LoreCommandRouting.opensGui("lore add LBIN"));
        assertFalse(LoreCommandRouting.opensGui("other"));
    }

    @Test
    void recognizesBackendSessionCommands() {
        assertTrue(LoreCommandRouting.changesBackendSession("cofl stop"));
        assertTrue(LoreCommandRouting.changesBackendSession("COFL reset"));
        assertFalse(LoreCommandRouting.changesBackendSession("cofl connect destination"));
        assertFalse(LoreCommandRouting.changesBackendSession("cofl status"));
        assertFalse(LoreCommandRouting.changesBackendSession("stop"));
    }
}
