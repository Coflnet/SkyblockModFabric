package com.coflnet.skyblock.testserver;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

final class SyntheticOperator {
    private static final UUID ID = UUID.nameUUIDFromBytes("skycofl-scenario-operator".getBytes(StandardCharsets.UTF_8));
    private final ServerPlayer player;
    private final RecordingConnection connection;

    private SyntheticOperator(ServerPlayer player, RecordingConnection connection) {
        this.player = player;
        this.connection = connection;
    }

    static SyntheticOperator create(MinecraftServer server) {
        var profile = new GameProfile(ID, "ScenarioOperator");
        var clientInformation = ClientInformation.createDefault();
        var player = new ServerPlayer(server, server.overworld(), profile, clientInformation);
        var connection = new RecordingConnection();
        server.getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
        return new SyntheticOperator(player, connection);
    }

    ServerPlayer player() {
        return player;
    }

    void beginRecording() {
        connection.clearRecording();
    }

    LabeledObservation packetObservation(Scenario scenario) {
        List<Class<? extends Packet<?>>> expected = scenario.id().equals("world-signals")
                ? List.of(ClientboundSetActionBarTextPacket.class, ClientboundSetTitleTextPacket.class,
                ClientboundPlayerInfoUpdatePacket.class, ClientboundForgetLevelChunkPacket.class,
                ClientboundLevelChunkWithLightPacket.class)
                : List.of(ClientboundContainerSetContentPacket.class, ClientboundContainerSetSlotPacket.class,
                ClientboundContainerClosePacket.class);
        boolean passed = expected.stream().allMatch(connection::recorded);
        return new LabeledObservation("network.clientbound-packets", passed,
                "synthetic operator recorded every required clientbound packet type");
    }
}
