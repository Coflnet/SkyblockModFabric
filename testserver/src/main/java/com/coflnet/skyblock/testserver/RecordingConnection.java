package com.coflnet.skyblock.testserver;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.util.ArrayList;
import java.util.List;

final class RecordingConnection extends Connection {
    private static final int MAX_RECORDED_PACKETS = 4096;
    private final List<Class<?>> packetTypes = new ArrayList<>();

    RecordingConnection() {
        super(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(this);
    }

    @Override
    public void send(Packet<?> packet) {
        record(packet);
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
        record(packet);
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
        record(packet);
    }

    private void record(Packet<?> packet) {
        if (packetTypes.size() == MAX_RECORDED_PACKETS) {
            throw new IllegalStateException("synthetic operator packet recording exceeded 4096 packets");
        }
        packetTypes.add(packet.getClass());
    }

    void clearRecording() {
        packetTypes.clear();
    }

    boolean recorded(Class<?> packetType) {
        return packetTypes.stream().anyMatch(packetType::isAssignableFrom);
    }
}
