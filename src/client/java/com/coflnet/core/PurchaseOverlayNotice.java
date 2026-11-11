package com.coflnet.core;

import java.util.Optional;

/** Warns when {@code /cofl setgui} enables a custom purchase overlay that replaces Hypixel's menu. */
public final class PurchaseOverlayNotice {
    private PurchaseOverlayNotice() {}

    public static final String MESSAGE = "§e[SkyCofl] Note: replacing Hypixel's purchase menu with a custom overlay"
            + " may be against the Hypixel rules. Use §a/cofl setgui off §eto turn it off again.";

    /** @param args the {@code /cofl} arguments split on spaces */
    public static Optional<String> forCommand(String[] args) {
        if (args == null || args.length < 2 || !args[0].equalsIgnoreCase("setgui")) {
            return Optional.empty();
        }
        String gui = args[1];
        return gui.equalsIgnoreCase("cofl") || gui.equalsIgnoreCase("tfm") ? Optional.of(MESSAGE) : Optional.empty();
    }
}
