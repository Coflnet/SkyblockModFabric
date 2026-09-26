package com.coflnet.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TradeGuiCommandTest {
    @Test void completesLocalCommandAndStatesAtTheCorrectCursorForBothAliases() {
        var dispatcher = dispatcher(new AtomicBoolean(), new ArrayList<>(), new AtomicInteger());
        for (String alias : List.of("cofl", "cl")) {
            for (String input : List.of(alias + " ", alias + " tr")) {
                var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
                assertTrue(suggestions.getList().stream().anyMatch(s -> s.getText().equals("tradegui")));
                assertTrue(suggestions.getList().stream().anyMatch(s -> s.apply(input).equals(alias + " tradegui")));
            }
            for (String suffix : List.of(" ", " o")) {
                String input = alias + " tradegui" + suffix;
                var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
                assertEquals(List.of("off", "on"), suggestions.getList().stream().map(s -> s.getText()).toList());
                assertEquals(List.of(alias + " tradegui off", alias + " tradegui on"),
                        suggestions.getList().stream().map(s -> s.apply(input)).toList());
            }
        }
    }

    @Test void literalCommandsToggleLocallyAndStatusDoesNotChangeTheSetting() throws Exception {
        var enabled = new AtomicBoolean();
        var feedback = new ArrayList<String>();
        var forwarded = new AtomicInteger();
        var dispatcher = dispatcher(enabled, feedback, forwarded);
        assertEquals(1, dispatcher.execute("cofl tradegui on", new Object()));
        assertTrue(enabled.get());
        assertTrue(feedback.getLast().contains("enabled"));
        assertEquals(1, dispatcher.execute("cl tradegui", new Object()));
        assertTrue(enabled.get());
        assertTrue(feedback.get(feedback.size() - 2).contains("§aon"));
        assertEquals(1, dispatcher.execute("cl tradegui off", new Object()));
        assertFalse(enabled.get());
        assertTrue(feedback.getLast().contains("disabled"));
        assertEquals(1, dispatcher.execute("cl tradegui ON", new Object()));
        assertTrue(enabled.get());
        assertEquals(1, dispatcher.execute("cofl tradegui invalid", new Object()));
        assertTrue(enabled.get());
        assertEquals(0, forwarded.get());
        assertEquals(1, dispatcher.execute("cofl help", new Object()));
        assertEquals(1, forwarded.get());
    }

    @Test void backendUpdatesStillProvideSuggestionsAndReceiveTheirFullArguments() throws Exception {
        var commands = new HashMap<String, String>();
        commands.put("report", "Report an issue");
        var forwardedArguments = new ArrayList<String>();
        var dispatcher = dispatcher(new AtomicBoolean(), new ArrayList<>(), new AtomicInteger(),
                commands, forwardedArguments);
        for (String alias : List.of("cofl", "cl")) {
            String input = alias + " re";
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
            assertEquals(List.of(alias + " report"), suggestions.getList().stream().map(s -> s.apply(input)).toList());
            assertEquals("Report an issue", suggestions.getList().getFirst().getTooltip().getString());
            assertEquals(1, dispatcher.execute(alias + " report trade pricing issue", new Object()));
        }
        assertEquals(List.of("report trade pricing issue", "report trade pricing issue"), forwardedArguments);
        commands.clear();
        commands.put("settings", "Mod settings");
        String input = "cofl ";
        var updated = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
        assertEquals(List.of("settings", "tradegui"), updated.getList().stream().map(s -> s.getText()).toList());
        commands.clear();
        var offline = dispatcher.getCompletionSuggestions(dispatcher.parse(input, new Object())).join();
        assertEquals(List.of("tradegui"), offline.getList().stream().map(s -> s.getText()).toList());
    }

    private static CommandDispatcher<Object> dispatcher(AtomicBoolean enabled, List<String> feedback,
                                                         AtomicInteger forwarded) {
        return dispatcher(enabled, feedback, forwarded, Map.of(), new ArrayList<>());
    }

    private static CommandDispatcher<Object> dispatcher(AtomicBoolean enabled, List<String> feedback,
                                                         AtomicInteger forwarded, Map<String, String> commands,
                                                         List<String> forwardedArguments) {
        var dispatcher = new CommandDispatcher<Object>();
        for (String alias : List.of("cofl", "cl")) {
            dispatcher.register(LiteralArgumentBuilder.<Object>literal(alias)
                    .then(TradeGuiCommand.<Object>create(enabled::get, enabled::set, feedback::add))
                    .then(RequiredArgumentBuilder.<Object, String>argument("args", StringArgumentType.greedyString())
                            .suggests((context, builder) -> {
                                commands.forEach((command, description) -> {
                                    if (command.startsWith(builder.getRemaining())) {
                                        builder.suggest(command, () -> description);
                                    }
                                });
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                forwarded.incrementAndGet();
                                forwardedArguments.add(StringArgumentType.getString(context, "args"));
                                return 1;
                            })));
        }
        return dispatcher;
    }
}
