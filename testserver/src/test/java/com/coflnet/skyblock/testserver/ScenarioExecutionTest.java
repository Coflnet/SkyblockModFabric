package com.coflnet.skyblock.testserver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScenarioExecutionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        FixtureLoaderTest.bootstrapMinecraft();
    }

    @Test
    void skyblockMenuMarkerMatchesProductionInventoryGate() {
        var customData = FixtureLoader.skyblockMenuData();
        assertEquals("SKYBLOCK_MENU", customData.copyTag().getStringOr("id", ""));
        assertTrue(customData.toString().contains("id:\"SKYBLOCK_MENU\""));
    }

    @Test
    void delayedPacketStepsCannotPassBeforeTheyExecuteAndResetClearsState() {
        var scheduler = new DeterministicScheduler();
        var steps = new ExecutionSteps();
        assertFalse(steps.completedInOrder(ScenarioExpectations.MENU_PACKET_ORDER));
        scheduler.schedule(8, () -> steps.record("menu.slot-update-older"));
        scheduler.schedule(6, () -> steps.record("menu.slot-update-newer"));
        scheduler.schedule(16, () -> steps.record("menu.close"));
        scheduler.schedule(24, () -> steps.record("menu.reopen"));
        scheduler.tick(7);
        assertEquals(java.util.List.of("menu.slot-update-newer"), steps.snapshot());
        scheduler.tick(24);
        assertTrue(steps.completedInOrder(ScenarioExpectations.MENU_PACKET_ORDER));
        assertEquals(ScenarioExpectations.MENU_PACKET_ORDER, steps.snapshot());

        scheduler.schedule(30, () -> steps.record("stale.action"));
        scheduler.reset();
        scheduler.tick(30);
        steps.reset();
        assertTrue(steps.snapshot().isEmpty());
        assertFalse(steps.completedInOrder(ScenarioExpectations.MENU_PACKET_ORDER));
    }

    @Test
    void directorStartAndResetClearServerStateAndRebuildTheSelectedScenario() {
        var server = mock(net.minecraft.server.MinecraftServer.class);
        var playerList = mock(net.minecraft.server.players.PlayerList.class);
        var level = mock(net.minecraft.server.level.ServerLevel.class);
        var entity = mock(net.minecraft.world.entity.Entity.class);
        var scoreboard = mock(net.minecraft.server.ServerScoreboard.class);
        var objective = mock(net.minecraft.world.scores.Objective.class);
        var team = mock(net.minecraft.world.scores.PlayerTeam.class);
        var emptyWorld = mock(EmptyScenarioWorld.class);
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var room = ScenarioCatalog.load().require("ender-chest-sequence").room();

        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayers()).thenReturn(List.of());
        when(server.getAllLevels()).thenReturn(List.of(level));
        when(server.overworld()).thenReturn(level);
        when(server.getScoreboard()).thenReturn(scoreboard);
        when(server.registryAccess()).thenReturn(registries);
        when(level.getAllEntities()).thenReturn(List.of(entity));
        when(scoreboard.getObjectives()).thenReturn(java.util.Set.of(objective));
        when(scoreboard.getPlayerTeams()).thenReturn(java.util.Set.of(team));
        when(level.getBlockState(any())).thenAnswer(invocation -> {
            var position = invocation.getArgument(0, net.minecraft.core.BlockPos.class);
            if (position.equals(new net.minecraft.core.BlockPos(room.x(), room.y(), room.z()))) {
                return net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (position.equals(new net.minecraft.core.BlockPos(room.x() + 4, room.y() + 3, room.z()))) {
                return net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState();
            }
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        });

        var director = new ScenarioDirector(ScenarioCatalog.load(), emptyWorld);
        var started = director.start(server, null, "ender-chest-sequence");
        var reset = director.reset(server, null);

        assertEquals("ender-chest-sequence", director.current().id());
        assertEquals("default:ender-chest-sequence", director.fixtureDigest());
        assertTrue(started.stream().filter(value -> value.label().equals("room.rebuilt"))
                .findFirst().orElseThrow().passed());
        assertTrue(reset.stream().filter(value -> value.label().equals("room.rebuilt"))
                .findFirst().orElseThrow().passed());
        verify(entity, times(2)).discard();
        verify(scoreboard, times(2)).removeObjective(objective);
        verify(scoreboard, times(2)).removePlayerTeam(team);
        verify(emptyWorld, times(2)).clearActive(level);
        verify(emptyWorld, times(2)).prepareRoom(level, director.current().room());
        verify(level, times(2)).setBlockAndUpdate(
                new net.minecraft.core.BlockPos(room.x(), room.y(), room.z()),
                net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
    }

    @Test
    void automatedResultFailsWhenAnyRealExecutionObservationIsMissing() {
        var observations = new ArrayList<LabeledObservation>();
        observations.add(new LabeledObservation("menu.initial-contents", true, "initial packet sent"));
        observations.add(new LabeledObservation("menu.reopen", false, "delayed reopen did not execute"));
        var result = ScenarioResultWriter.Result.from("ender-chest-sequence", observations);
        assertFalse(result.passed());
        assertEquals("labels=menu.initial-contents:true,menu.reopen:false", result.summary());
    }

    @Test
    void automatedRunWaitsForDelayedActionsBeforeProducingAResult() {
        var state = new AutomatedRunState();
        state.reset(10);
        state.begin(java.util.List.of(
                new LabeledObservation("menu.initial-contents", true, "initial packet sent")), 10);
        assertFalse(state.readyToAssert(41, false));
        assertFalse(state.readyToAssert(42, true));
        assertTrue(state.readyToAssert(42, false));

        var result = state.complete("ender-chest-sequence", java.util.List.of(
                new LabeledObservation("menu.reopen", true, "delayed reopen executed")));
        assertTrue(result.passed());
        assertEquals(1, state.scenarioIndex());
        assertFalse(state.hasActiveScenario());
    }

    @Test
    void roomCleanupFootprintIncludesAllFourCrossedChunks() {
        var expected = java.util.Set.of(
                new net.minecraft.world.level.ChunkPos(3, -1),
                new net.minecraft.world.level.ChunkPos(3, 0),
                new net.minecraft.world.level.ChunkPos(4, -1),
                new net.minecraft.world.level.ChunkPos(4, 0));
        var world = new EmptyScenarioWorld();
        var prepared = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        world.prepareRoom(new Scenario.Room(64, 96, 0), prepared::add);
        assertEquals(expected, prepared);

        var reset = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        world.clearActive(reset::add);
        assertEquals(expected, reset);
        var secondReset = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        world.clearActive(secondReset::add);
        assertTrue(secondReset.isEmpty());
    }

    @Test
    void recordingConnectionCapturesClientboundPacketTypesWithoutANetworkChannel() {
        var connection = new RecordingConnection();
        var packet = mock(net.minecraft.network.protocol.Packet.class);

        connection.send(packet);

        assertTrue(connection.recorded(net.minecraft.network.protocol.Packet.class));
        connection.clearRecording();
        assertFalse(connection.recorded(net.minecraft.network.protocol.Packet.class));
    }
}
