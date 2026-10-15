package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoDisplayDefaultsTest {
    @Test
    void allThreeSlotsAreEnabledByDefault() {
        assertTrue(InfoDisplayDefaults.defaultEnabled(1));
        assertTrue(InfoDisplayDefaults.defaultEnabled(2));
        assertTrue(InfoDisplayDefaults.defaultEnabled(3));
    }

    @Test
    void migrationNeededForMissingMarkerButNotForCurrentVersion() {
        assertTrue(InfoDisplayDefaults.needsMigration(0));
        assertFalse(InfoDisplayDefaults.needsMigration(InfoDisplayDefaults.CURRENT_VERSION));
    }

    @Test
    void unmigratedConfigEnablesOnlySlotThree() {
        assertTrue(InfoDisplayDefaults.enabledAfterMigration(3, false, 0));
        assertFalse(InfoDisplayDefaults.enabledAfterMigration(1, false, 0));
        assertFalse(InfoDisplayDefaults.enabledAfterMigration(2, false, 0));
    }

    @Test
    void slotDisabledAfterMigrationStaysDisabledOnNextLoad() {
        int version = 0;
        boolean enabled = InfoDisplayDefaults.enabledAfterMigration(3, false, version);
        assertTrue(enabled);
        version = InfoDisplayDefaults.CURRENT_VERSION;
        enabled = false; // player turns the slot off in the edit screen
        assertFalse(InfoDisplayDefaults.enabledAfterMigration(3, enabled, version));
    }
}
