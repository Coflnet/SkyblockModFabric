package com.coflnet.gui.flip;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlipHudCommandSuggestionsTest {
    @Test
    void suggestsTheRootCommand() {
        assertEquals(List.of("fliphud"), FlipHudCommandSuggestions.forInput(""));
        assertEquals(List.of("fliphud"), FlipHudCommandSuggestions.forInput("flip"));
        assertEquals(List.of(), FlipHudCommandSuggestions.forInput("other"));
    }

    @Test
    void suggestsMatchingActions() {
        assertEquals(
                List.of("fliphud on", "fliphud off", "fliphud move", "fliphud reset"),
                FlipHudCommandSuggestions.forInput("fliphud "));
        assertEquals(
                List.of("fliphud on", "fliphud off"),
                FlipHudCommandSuggestions.forInput("fliphud o"));
        assertEquals(
                List.of("fliphud move"),
                FlipHudCommandSuggestions.forInput("FLIPHUD M"));
        assertEquals(List.of(), FlipHudCommandSuggestions.forInput("fliphud on extra"));
    }

    @Test
    void suppliesActionsAtTheBrigadierCursor() throws Exception {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("cofl")
                .then(RequiredArgumentBuilder.<Object, String>argument(
                                "args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (String suggestion :
                                    FlipHudCommandSuggestions.forInput(builder.getRemaining())) {
                                builder.suggest(suggestion);
                            }
                            return builder.buildFuture();
                        })));

        List<String> suggestions = dispatcher.getCompletionSuggestions(
                        dispatcher.parse("cofl fliphud ", new Object()))
                .get(1, TimeUnit.SECONDS)
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .toList();

        assertEquals(
                List.of("fliphud move", "fliphud off", "fliphud on", "fliphud reset"),
                suggestions);
    }
}
