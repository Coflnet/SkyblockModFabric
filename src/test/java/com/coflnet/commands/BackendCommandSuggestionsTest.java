package com.coflnet.commands;

import CoflCore.commands.CommandSuggestions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendCommandSuggestionsTest {
    @Test
    void backendListControlsCompletionAndEveryCommandUsesForwarding() throws Exception {
        var commands = new HashMap<String, String>();
        commands.put("tradegui", "Trade overlay");
        commands.put("tradegui on", "Enable the trade overlay");
        commands.put("tradegui off", "Disable the trade overlay");
        commands.put("report", "Report an issue");
        var forwarded = new ArrayList<String>();
        var dispatcher = new CommandDispatcher<Object>();
        for (String alias : List.of("cofl", "cl")) {
            dispatcher.register(LiteralArgumentBuilder.<Object>literal(alias)
                    .then(RequiredArgumentBuilder.<Object, String>argument("args", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                for (var entry : CommandSuggestions.matching(commands, builder.getRemaining())) {
                                    builder.suggest(entry.getKey(), () -> entry.getValue());
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                forwarded.add(StringArgumentType.getString(context, "args"));
                                return 1;
                            })));
        }
        for (String alias : List.of("cofl", "cl")) {
            for (String prefix : List.of("tr", "TRA")) {
                String input = alias + " " + prefix;
                var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
                assertEquals(List.of(alias + " tradegui"), suggestions.getList().stream().map(s -> s.apply(input)).toList());
            }
            for (String suffix : List.of(" ", " o")) {
                String input = alias + " tradegui" + suffix;
                var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
                assertEquals(List.of(alias + " tradegui off", alias + " tradegui on"),
                        suggestions.getList().stream().map(s -> s.apply(input)).toList());
            }
            dispatcher.execute(alias + " tradegui on", new Object());
            dispatcher.execute(alias + " report trade pricing issue", new Object());
        }
        assertEquals(List.of("tradegui on", "report trade pricing issue", "tradegui on", "report trade pricing issue"), forwarded);
        commands.clear();
        commands.put("settings", "Mod settings");
        String input = "cofl ";
        var updated = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
        assertEquals(List.of("settings"), updated.getList().stream().map(s -> s.getText()).toList());
        commands.clear();
        assertTrue(dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join().isEmpty());
    }
}
