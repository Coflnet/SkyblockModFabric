package com.coflnet.gui.flip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FlipHudCommandSuggestions {
    private static final List<String> ACTIONS = List.of("on", "off", "move", "reset");

    private FlipHudCommandSuggestions() {
    }

    public static List<String> forInput(String input) {
        String remaining = input == null
                ? ""
                : input.stripLeading().toLowerCase(Locale.ROOT);
        int separator = remaining.indexOf(' ');
        if (separator < 0) {
            return "fliphud".startsWith(remaining)
                    ? List.of("fliphud")
                    : List.of();
        }
        if (!remaining.substring(0, separator).equals("fliphud")) {
            return List.of();
        }
        String actionPrefix = remaining.substring(separator + 1).stripLeading();
        if (actionPrefix.indexOf(' ') >= 0) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        for (String action : ACTIONS) {
            if (action.startsWith(actionPrefix)) {
                suggestions.add("fliphud " + action);
            }
        }
        return suggestions;
    }
}
