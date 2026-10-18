package com.coflnet.config;

/** The opt-in toggles of the local Bazaar order guidance and relist suggestions. */
public class BazaarAssistManager {
    public static boolean isGuidanceEnabled() {
        return CoflModConfig.get().bazaarOrderGuidanceEnabled;
    }

    public static void setGuidanceEnabled(boolean enabled) {
        CoflModConfig cfg = CoflModConfig.get();
        cfg.bazaarOrderGuidanceEnabled = enabled;
        cfg.save();
    }

    public static boolean isRelistEnabled() {
        return CoflModConfig.get().bazaarRelistSuggestionsEnabled;
    }

    public static void setRelistEnabled(boolean enabled) {
        CoflModConfig cfg = CoflModConfig.get();
        cfg.bazaarRelistSuggestionsEnabled = enabled;
        cfg.save();
    }
}
