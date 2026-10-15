package com.coflnet.skyblock.testserver;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioMod implements ModInitializer {
    private final ScenarioCatalog catalog;
    private final ScenarioDirector director;
    private final EmptyScenarioWorld emptyWorld;
    private final AutomatedRunState automatedState = new AutomatedRunState();
    private boolean automatedPending;
    private final List<ScenarioResultWriter.Result> automatedResults = new ArrayList<>();
    private String selectedFixtureDigest;
    private SyntheticOperator syntheticOperator;

    public ScenarioMod() {
        this.catalog = ScenarioCatalog.load();
        this.emptyWorld = new EmptyScenarioWorld();
        this.director = new ScenarioDirector(catalog, emptyWorld);
    }

    @Override
    public void onInitialize() {
        emptyWorld.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("skycofl-test")
                        .requires(source -> source.isPlayer()
                                && source.permissions().hasPermission(Permissions.COMMANDS_ADMIN))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(context -> start(context.getSource(),
                                                StringArgumentType.getString(context, "id")))))
                        .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                        .then(Commands.literal("assert").executes(context -> assertScenario(context.getSource())))
                        .then(Commands.literal("next").executes(context -> next(context.getSource())))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            director.tick(server);
            if (automatedPending) {
                runAutomated(server);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            automatedPending = ScenarioHostContract.automated();
            automatedResults.clear();
            selectedFixtureDigest = null;
            syntheticOperator = automatedPending ? SyntheticOperator.create(server) : null;
            automatedState.reset(server.getTickCount());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ScenarioHostContract.automated() && director.current() == null) {
                Scenario scenario = catalog.manualJoinScenario();
                director.start(server, handler.player, scenario.id());
            }
        });
    }

    private int list(CommandSourceStack source) {
        director.listLines().forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return catalog.scenarios().size();
    }

    private int start(CommandSourceStack source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var observations = director.start(source.getServer(), source.getPlayerOrException(), id);
            source.sendSuccess(() -> Component.literal("started " + id), false);
            return observations.stream().allMatch(LabeledObservation::passed) ? 1 : 0;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private int reset(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var observations = director.reset(source.getServer(), source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("reset " + director.current().id()), false);
        return observations.stream().allMatch(LabeledObservation::passed) ? 1 : 0;
    }

    private int assertScenario(CommandSourceStack source) {
        var observations = director.assertCurrent(source.getServer());
        return observations.stream().allMatch(LabeledObservation::passed) ? 1 : 0;
    }

    private int next(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var observations = director.next(source.getServer(), source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("started " + director.current().id()), false);
        return observations.stream().allMatch(LabeledObservation::passed) ? 1 : 0;
    }

    private void runAutomated(MinecraftServer server) {
        if (!automatedState.hasActiveScenario()) {
            Scenario scenario = catalog.scenarios().get(automatedState.scenarioIndex());
            syntheticOperator.beginRecording();
            automatedState.begin(director.start(server, syntheticOperator.player(), scenario.id()), server.getTickCount());
            if (!director.fixtureDigest().startsWith("default:")) selectedFixtureDigest = director.fixtureDigest();
            return;
        }
        if (!automatedState.readyToAssert(server.getTickCount(), director.hasPendingActions())) return;
        Scenario scenario = catalog.scenarios().get(automatedState.scenarioIndex());
        var assertions = new ArrayList<>(director.assertCurrent(server));
        assertions.add(syntheticOperator.packetObservation(scenario));
        automatedResults.add(automatedState.complete(scenario.id(), assertions));
        if (automatedState.scenarioIndex() == catalog.scenarios().size()) finishAutomated(server);
    }

    private void finishAutomated(MinecraftServer server) {
        automatedPending = false;
        ScenarioResultWriter.write(automatedResults, selectedFixtureDigest);
        server.halt(false);
    }
}
