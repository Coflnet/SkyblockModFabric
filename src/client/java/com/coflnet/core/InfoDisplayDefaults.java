package com.coflnet.core;

/** Default enabled state and one-time config migration for the info display slots, unit tested in isolation. */
public final class InfoDisplayDefaults {
    /** Bump when a new migration step is added; fresh configs start at this version. */
    public static final int CURRENT_VERSION = 1;

    /** Slot that stayed disabled in pre-release configs although the backend now sends goals to it. */
    private static final int GOAL_SLOT = 3;

    private InfoDisplayDefaults() {
    }

    /** All three slots are enabled by default; an empty slot draws nothing. */
    public static boolean defaultEnabled(int id) {
        return id >= 1 && id <= 3;
    }

    /** {@code version} is the persisted marker; a file without it reads as 0. */
    public static boolean needsMigration(int version) {
        return version < CURRENT_VERSION;
    }

    /** Enabled state a stored slot should have after load; only the goal slot is forced on, once. */
    public static boolean enabledAfterMigration(int id, boolean enabled, int version) {
        return id == GOAL_SLOT && needsMigration(version) ? true : enabled;
    }
}
