package com.coflnet.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class TradeGuiCommand {
    private TradeGuiCommand() {
    }

    public static <S> LiteralArgumentBuilder<S> create(BooleanSupplier enabled, Consumer<Boolean> setEnabled,
                                                      Consumer<String> feedback) {
        return LiteralArgumentBuilder.<S>literal("tradegui")
                .executes(context -> execute(new String[]{"tradegui"}, enabled, setEnabled, feedback))
                .then(LiteralArgumentBuilder.<S>literal("on")
                        .executes(context -> execute(new String[]{"tradegui", "on"}, enabled, setEnabled, feedback)))
                .then(LiteralArgumentBuilder.<S>literal("off")
                        .executes(context -> execute(new String[]{"tradegui", "off"}, enabled, setEnabled, feedback)))
                .then(RequiredArgumentBuilder.<S, String>argument("state", StringArgumentType.word())
                        .executes(context -> execute(new String[]{"tradegui", StringArgumentType.getString(context, "state")},
                                enabled, setEnabled, feedback)));
    }

    public static int execute(String[] args, BooleanSupplier enabled, Consumer<Boolean> setEnabled,
                              Consumer<String> feedback) {
        if (args.length >= 2 && (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            boolean value = args[1].equalsIgnoreCase("on");
            setEnabled.accept(value);
            feedback.accept("§aTrade overlay " + (value ? "§aenabled" : "§cdisabled")
                    + "§7. Open a trade to " + (value ? "use the SkyCofl trade GUI." : "use the normal Hypixel window."));
        } else {
            feedback.accept("§7Trade overlay is currently " + (enabled.getAsBoolean() ? "§aon" : "§coff"));
            feedback.accept("§7Usage: §e/cofl tradegui <on/off>");
        }
        return 1;
    }
}
