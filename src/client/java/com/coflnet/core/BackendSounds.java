package com.coflnet.core;

public final class BackendSounds {
    private BackendSounds() {
    }

    /**
     * Maps a sound name sent by the backend (legacy 1.8 names) to the constant holding it in
     * Minecraft's {@code SoundEvents}; empty when the sound is unknown, which plays the default.
     */
    public static String soundEventField(String backendName) {
        if (backendName == null) return "";
        return switch (backendName) {
            case "note.bass" -> "NOTE_BLOCK_BASS";
            case "note.pling" -> "NOTE_BLOCK_PLING";
            case "note.hat" -> "NOTE_BLOCK_HAT";
            case "random.orb" -> "EXPERIENCE_ORB_PICKUP";
            default -> "";
        };
    }
}
