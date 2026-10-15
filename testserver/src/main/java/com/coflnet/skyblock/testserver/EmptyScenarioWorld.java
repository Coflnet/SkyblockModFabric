package com.coflnet.skyblock.testserver;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Keeps every generated or loaded chunk empty except the currently selected owned room. */
public final class EmptyScenarioWorld {
    private Set<ChunkPos> activeRoomChunks = Set.of();

    public void register() {
        ServerChunkEvents.CHUNK_GENERATE.register((level, chunk) -> clearUnlessActive(level, chunk));
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newlyGenerated) -> clearUnlessActive(level, chunk));
    }

    public void clearActive(ServerLevel level) {
        clearActive(position -> {
            clearChunk(level, requireChunk(level, position));
            level.setChunkForced(position.x(), position.z(), false);
        });
    }

    public void prepareRoom(ServerLevel level, Scenario.Room room) {
        prepareRoom(room, position -> {
            level.setChunkForced(position.x(), position.z(), true);
            clearChunk(level, requireChunk(level, position));
        });
    }

    public void clearScheduledUpdates(ServerLevel level, ChunkPos chunkPos) {
        var bounds = new BoundingBox(chunkPos.getMinBlockX(), level.getMinY(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX(), level.getMaxY(), chunkPos.getMaxBlockZ());
        level.getBlockTicks().clearArea(bounds);
        level.getFluidTicks().clearArea(bounds);
    }

    private void clearUnlessActive(ServerLevel level, LevelChunk chunk) {
        if (!activeRoomChunks.contains(chunk.getPos())) clearChunk(level, chunk);
    }

    static Set<ChunkPos> roomChunks(Scenario.Room room) {
        var chunks = new LinkedHashSet<ChunkPos>();
        chunks.add(ChunkPos.containing(new BlockPos(room.x() - 4, room.y(), room.z() - 4)));
        chunks.add(ChunkPos.containing(new BlockPos(room.x() - 4, room.y(), room.z() + 4)));
        chunks.add(ChunkPos.containing(new BlockPos(room.x() + 4, room.y(), room.z() - 4)));
        chunks.add(ChunkPos.containing(new BlockPos(room.x() + 4, room.y(), room.z() + 4)));
        return Set.copyOf(chunks);
    }

    void clearActive(Consumer<ChunkPos> resetChunk) {
        activeRoomChunks.forEach(resetChunk);
        activeRoomChunks = Set.of();
    }

    void prepareRoom(Scenario.Room room, Consumer<ChunkPos> resetChunk) {
        Set<ChunkPos> selected = roomChunks(room);
        selected.forEach(resetChunk);
        activeRoomChunks = selected;
    }

    private void clearChunk(ServerLevel level, LevelChunk chunk) {
        for (var section : chunk.getSections()) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        section.setBlockState(x, y, z, Blocks.AIR.defaultBlockState(), false);
                    }
                }
            }
            section.recalcBlockCounts();
        }
        chunk.clearAllBlockEntities();
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        clearScheduledUpdates(level, chunk.getPos());
        chunk.markUnsaved();
    }

    private static LevelChunk requireChunk(ServerLevel level, ChunkPos position) {
        var chunk = level.getChunkSource().getChunk(position.x(), position.z(), ChunkStatus.FULL, true);
        if (!(chunk instanceof LevelChunk loaded)) {
            throw new IllegalStateException("unable to load scenario chunk " + position);
        }
        return loaded;
    }
}
