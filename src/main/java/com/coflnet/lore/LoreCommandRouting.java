package com.coflnet.lore;

public final class LoreCommandRouting {
    private LoreCommandRouting() {
    }

    public static boolean opensGui(String rawArguments) {
        if (rawArguments == null) {
            return false;
        }
        String trimmed = rawArguments.trim();
        return trimmed.equalsIgnoreCase("lore")
                || trimmed.equalsIgnoreCase("loregui");
    }

    public static boolean changesBackendSession(String command) {
        if (command == null || command.length() > 256) {
            return false;
        }
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 2) {
            return false;
        }
        String root = parts[0];
        if (!"cofl".equalsIgnoreCase(root) && !"cl".equalsIgnoreCase(root)) {
            return false;
        }
        return switch (parts[1].toLowerCase(java.util.Locale.ROOT)) {
            case "start", "stop", "reset" -> true;
            default -> false;
        };
    }
}
