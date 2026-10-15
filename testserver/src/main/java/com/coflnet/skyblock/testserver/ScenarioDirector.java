package com.coflnet.skyblock.testserver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioDirector {
    private static final String OBJECTIVE = "skycofl_test";
    private static final String TEAM = "skycofl_signal";
    private final ScenarioCatalog catalog;
    private final EmptyScenarioWorld emptyWorld;
    private final DeterministicScheduler scheduler = new DeterministicScheduler();
    private final ExecutionSteps executedSteps = new ExecutionSteps();
    private Scenario current;
    private FixtureMenu fixture;

    public ScenarioDirector(ScenarioCatalog catalog, EmptyScenarioWorld emptyWorld) {
        this.catalog = catalog;
        this.emptyWorld = emptyWorld;
    }

    public Scenario current() {
        return current;
    }

    public String fixtureDigest() {
        return fixture == null ? null : fixture.digest();
    }

    public List<String> listLines() {
        return catalog.scenarios().stream()
                .map(value -> value.id() + " — " + value.title()
                        + " [manual=" + value.manual() + ", automated=" + value.automated() + "]")
                .toList();
    }

    public List<LabeledObservation> start(MinecraftServer server, ServerPlayer operator, String id) {
        return start(server, operator, catalog.require(id));
    }

    public List<LabeledObservation> reset(MinecraftServer server, ServerPlayer operator) {
        if (current == null) throw new IllegalStateException("no scenario selected");
        return start(server, operator, current);
    }

    public List<LabeledObservation> next(MinecraftServer server, ServerPlayer operator) {
        int index = current == null ? -1 : catalog.indexOf(current);
        return start(server, operator, catalog.scenarios().get((index + 1) % catalog.scenarios().size()));
    }

    private List<LabeledObservation> start(MinecraftServer server, ServerPlayer operator, Scenario scenario) {
        clearState(server);
        current = scenario;
        fixture = FixtureLoader.selectedOrDefault(server.registryAccess(), scenario.id());
        ServerLevel level = server.overworld();
        emptyWorld.prepareRoom(level, scenario.room());
        buildRoom(level, scenario);
        if (operator != null) {
            operator.teleportTo(level, scenario.room().x() + 0.5, scenario.room().y() + 1,
                    scenario.room().z() + 0.5, java.util.Set.of(), 0, 0, true);
        }
        var observations = new ArrayList<LabeledObservation>();
        observations.add(new LabeledObservation("room.rebuilt", roomIsBuilt(level, scenario.room()),
                "selected room rebuilt from owned stone-brick geometry"));
        switch (scenario.id()) {
            case "ender-chest-sequence", "bazaar-menu", "bazaar-orders", "auction-house-menu", "trade-divider" ->
                    setupMenu(operator, scenario, observations);
            case "world-signals" -> setupWorldSignals(server, operator, observations);
            default -> throw new IllegalStateException("scenario has no director: " + scenario.id());
        }
        announce(operator, observations);
        return List.copyOf(observations);
    }

    public List<LabeledObservation> assertCurrent(MinecraftServer server) {
        if (current == null) throw new IllegalStateException("no scenario selected");
        var checks = new ArrayList<LabeledObservation>();
        checks.add(new LabeledObservation("room.structure", roomIsBuilt(server.overworld(), current.room()),
                "room floor and boundary blocks match the deterministic blueprint"));
        checks.add(new LabeledObservation("fixture.transition", fixture != null && fixture.items().size() == 54,
                "SkyUserState-converted menu or owned default produced 54 bounded slots"));
        if (current.id().equals("trade-divider")) {
            boolean dividers = true;
            for (int slot : new int[]{4, 13, 22, 31, 40}) dividers &= !fixture.items().get(slot).isEmpty();
            checks.add(new LabeledObservation("trade.dividers", dividers,
                    "all five divider slots in the 45-slot trade layout are populated"));
        }
        if (current.id().equals("world-signals")) {
            checks.add(new LabeledObservation("world.entities", hasOwnedEntities(server.overworld()),
                    "named armor stand and mob exist for metadata observations"));
            checks.add(new LabeledObservation("scoreboard.sidebar", hasObjective(server.getScoreboard()),
                    "objective, team and score updates exist on the real server scoreboard"));
            checks.add(executed("entity.metadata-update", "scheduled entity metadata mutation executed"));
            checks.add(executed("chunk.unload", "forget-chunk packet was sent"));
            checks.add(executed("chunk.reload", "level-chunk-with-light packet was sent"));
            checks.add(new LabeledObservation("world.packet-order",
                    executedSteps.completedInOrder(ScenarioExpectations.WORLD_PACKET_ORDER),
                    "metadata mutation, chunk unload and chunk reload executed in canonical order"));
        } else {
            checks.add(executed("menu.slot-update-newer", "newer state-id slot update was sent"));
            checks.add(executed("menu.slot-update-older", "older state-id slot update was sent after the newer update"));
            checks.add(executed("menu.close", "container close packet was sent"));
            checks.add(executed("menu.reopen", "container was reopened after close"));
            checks.add(new LabeledObservation("menu.packet-order",
                    executedSteps.completedInOrder(ScenarioExpectations.MENU_PACKET_ORDER),
                    "delayed slot updates, close and reopen executed in canonical order"));
        }
        announce(server.getPlayerList().getPlayers().stream().findFirst().orElse(null), checks);
        return List.copyOf(checks);
    }

    public void tick(MinecraftServer server) {
        tickAt(server.getTickCount());
    }

    public boolean hasPendingActions() {
        return scheduler.hasPendingActions();
    }

    private void setupMenu(ServerPlayer operator, Scenario scenario, List<LabeledObservation> observations) {
        int slots = scenario.id().equals("trade-divider") ? 45 : 54;
        if (operator == null) {
            observations.add(new LabeledObservation("menu.operator-connection", false,
                    "a connected operator is required to exercise real container packets"));
            return;
        }
        operator.getInventory().setItem(8, FixtureLoader.skyblockMenuItem());
        observations.add(new LabeledObservation("menu.slot-8.skyblock-menu", isSkyblockMenu(operator.getInventory().getItem(8)),
                "player inventory slot 8 contains minecraft:custom_data id SKYBLOCK_MENU"));
        if (scenario.id().equals("bazaar-orders")) {
            observeBazaarOrderFixture(observations);
        }
        openMenu(operator, slots);
        int openContainer = operator.containerMenu.containerId;
        int state = operator.containerMenu.getStateId();
        operator.connection.send(new ClientboundContainerSetContentPacket(openContainer, state,
                operator.containerMenu.getItems(), ItemStack.EMPTY));
        observations.add(new LabeledObservation("menu.initial-contents", true,
                "initial contents were sent through the connected operator's packet listener"));
        long now = operator.level().getServer().getTickCount();
        scheduleMenuSequence(now, () -> {
            operator.connection.send(new ClientboundContainerSetSlotPacket(
                    openContainer, state + 2, 10, named(Items.EMERALD, "slot.update.newer")));
        }, () -> {
            operator.connection.send(new ClientboundContainerSetSlotPacket(
                    openContainer, state + 1, 10, named(Items.GOLD_INGOT, "slot.update.older")));
        }, () -> {
            operator.connection.send(new ClientboundContainerClosePacket(openContainer));
            operator.closeContainer();
        }, () -> {
            openMenu(operator, slots);
        });
    }

    private void observeBazaarOrderFixture(List<LabeledObservation> observations) {
        ItemStack order = fixture.items().get(13);
        var customData = order.get(DataComponents.CUSTOM_DATA);
        var tag = customData == null ? null : customData.copyTag();
        observations.add(new LabeledObservation("fixture.bazaar-order-tag",
                tag != null && "AGATHA_COUPON".equals(tag.getStringOr("Tag", "")),
                "order fixture contains sanitized Tag identity AGATHA_COUPON before initial contents are sent"));
        boolean hasDescription = order.get(DataComponents.LORE) != null
                && order.get(DataComponents.LORE).lines().stream()
                .map(Component::getString)
                .anyMatch(line -> line.equals("Order details:"));
        observations.add(new LabeledObservation("fixture.bazaar-order-description", hasDescription
                        && tag != null && "Order details:".equals(tag.getStringOr("Description", "")),
                "order fixture contains exact Description data and visible lore before initial contents are sent"));
    }

    private void openMenu(ServerPlayer operator, int slots) {
        operator.openMenu(new SimpleMenuProvider((containerId, inventory, player) -> {
            ChestMenu menu = slots == 45 ? ChestMenu.fiveRows(containerId, inventory)
                    : ChestMenu.sixRows(containerId, inventory);
            for (int slot = 0; slot < slots; slot++) menu.getContainer().setItem(slot, fixture.items().get(slot).copy());
            return menu;
        }, Component.literal(fixture.title())));
    }

    private void setupWorldSignals(MinecraftServer server, ServerPlayer operator,
                                   List<LabeledObservation> observations) {
        ServerLevel level = server.overworld();
        var room = current.room();
        var scoreboard = server.getScoreboard();
        var objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.literal("SKYCOFL SIGNALS"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        PlayerTeam team = scoreboard.addPlayerTeam(TEAM);
        team.setColor(ChatFormatting.AQUA);
        team.setPlayerPrefix(Component.literal(ScenarioExpectations.LOCATION_PREFIX));
        scoreboard.addPlayerToTeam(ScenarioExpectations.LOCATION_ENTRY, team);
        scoreboard.getOrCreatePlayerScore(() -> ScenarioExpectations.PURSE_LINE, objective).set(7);
        scoreboard.getOrCreatePlayerScore(() -> ScenarioExpectations.LOCATION_ENTRY, objective).set(6);
        boolean actorsSpawned = spawnActors(level, room);
        observations.add(new LabeledObservation("scoreboard.objective-team-score",
                hasObjective(scoreboard) && team.getPlayers().contains(ScenarioExpectations.LOCATION_ENTRY),
                "separate Purse and location score lines were applied to the real scoreboard"));
        observations.add(new LabeledObservation("entity.named", actorsSpawned,
                "named armor stand and mob were spawned"));
        if (operator == null) {
            observations.add(new LabeledObservation("hud.operator-connection", false,
                    "a connected operator is required to exercise HUD, tab, sound and chunk packets"));
            return;
        }
        operator.sendSystemMessage(Component.literal("[hud.chat-actionbar-title-tab-sound] scenario chat"));
        operator.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("scenario action bar")));
        operator.connection.send(new ClientboundSetTitlesAnimationPacket(5, 30, 5));
        operator.connection.send(new ClientboundSetTitleTextPacket(Component.literal("SKYCOFL SCENARIO")));
        operator.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("world-signals")));
        operator.connection.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, operator));
        level.playSound(null, new BlockPos(room.x(), room.y(), room.z()), SoundEvents.PLAYER_LEVELUP,
                SoundSource.MASTER, 1.0f, 1.0f);
        level.playSound(null, room.x(), room.y(), room.z(), SoundEvents.MUSIC_GAME, SoundSource.MUSIC, 0.25f, 1.0f);
        observations.add(new LabeledObservation("hud.chat-actionbar-title-tab-sound", true,
                "HUD, tab, sound and music events were sent to the connected operator"));
        long now = server.getTickCount();
        schedule(now + 8, () -> {
            mutateActors(level);
            executedSteps.record("entity.metadata-update");
        });
        ChunkPos chunk = ChunkPos.containing(new BlockPos(room.x(), room.y(), room.z()));
        schedule(now + 12, () -> {
            operator.connection.send(new ClientboundForgetLevelChunkPacket(chunk));
            executedSteps.record("chunk.unload");
        });
        schedule(now + 16, () -> {
            var loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded != null) {
                operator.connection.send(new ClientboundLevelChunkWithLightPacket(
                        loaded, level.getChunkSource().getLightEngine(), null, null));
                executedSteps.record("chunk.reload");
            }
        });
    }

    private boolean spawnActors(ServerLevel level, Scenario.Room room) {
        boolean standSpawned = false;
        var stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        if (stand != null) {
            stand.setPos(room.x() + 2.5, room.y() + 1, room.z() + 2.5);
            stand.setCustomName(Component.literal("scenario.actor.armor-stand"));
            stand.setCustomNameVisible(true);
            stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            standSpawned = level.addFreshEntity(stand);
        }
        boolean mobSpawned = false;
        var mob = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (mob != null) {
            mob.setPos(room.x() - 2.5, room.y() + 1, room.z() + 2.5);
            mob.setCustomName(Component.literal("scenario.actor.mob"));
            mob.setCustomNameVisible(true);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mobSpawned = level.addFreshEntity(mob);
        }
        return standSpawned && mobSpawned;
    }

    private void mutateActors(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity.getCustomName() != null && entity.getCustomName().getString().startsWith("scenario.actor.")) {
                entity.setGlowingTag(true);
                entity.setCustomName(Component.literal(entity.getCustomName().getString() + ".updated"));
            }
        }
    }

    private void clearState(MinecraftServer server) {
        resetExecutionState();
        var connectedPlayers = server.getPlayerList().getPlayers();
        var connectedIds = connectedPlayers.stream().map(ServerPlayer::getUUID).toList();
        var previousChunks = current == null ? java.util.Set.<ChunkPos>of()
                : EmptyScenarioWorld.roomChunks(current.room());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.closeContainer();
            player.getInventory().clearContent();
            player.connection.send(new ClientboundStopSoundPacket(null, null));
            player.connection.send(new ClientboundClearTitlesPacket(true));
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
            player.connection.send(new ClientboundPlayerInfoRemovePacket(connectedIds));
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(connectedPlayers));
            for (ChunkPos previousChunk : previousChunks) {
                player.connection.send(new ClientboundForgetLevelChunkPacket(previousChunk));
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            var entities = new ArrayList<Entity>();
            level.getAllEntities().forEach(entities::add);
            entities.stream().filter(entity -> !(entity instanceof ServerPlayer)).forEach(Entity::discard);
        }
        ServerScoreboard scoreboard = server.getScoreboard();
        new ArrayList<>(scoreboard.getObjectives()).forEach(scoreboard::removeObjective);
        new ArrayList<>(scoreboard.getPlayerTeams()).forEach(scoreboard::removePlayerTeam);
        emptyWorld.clearActive(server.overworld());
    }

    private void buildRoom(ServerLevel level, Scenario scenario) {
        Scenario.Room room = scenario.room();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(room.x() + dx, room.y(), room.z() + dz),
                        Blocks.STONE_BRICKS.defaultBlockState());
                if (Math.abs(dx) == 4 || Math.abs(dz) == 4) {
                    for (int dy = 1; dy <= 3; dy++) {
                        level.setBlockAndUpdate(new BlockPos(room.x() + dx, room.y() + dy, room.z() + dz),
                                dy == 3 ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.GLASS.defaultBlockState());
                    }
                }
            }
        }
        level.setBlockAndUpdate(new BlockPos(room.x() + 1, room.y(), room.z() + 1),
                (scenario.seed() & 1) == 0
                        ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
    }

    private boolean roomIsBuilt(ServerLevel level, Scenario.Room room) {
        return level.getBlockState(new BlockPos(room.x(), room.y(), room.z())).is(Blocks.STONE_BRICKS)
                && level.getBlockState(new BlockPos(room.x() + 4, room.y() + 3, room.z())).is(Blocks.SEA_LANTERN);
    }

    private boolean hasOwnedEntities(ServerLevel level) {
        boolean armorStand = false;
        boolean mob = false;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getCustomName() == null) continue;
            String name = entity.getCustomName().getString();
            armorStand |= name.startsWith("scenario.actor.armor-stand");
            mob |= name.startsWith("scenario.actor.mob");
        }
        return armorStand && mob;
    }

    private boolean hasObjective(ServerScoreboard scoreboard) {
        return scoreboard.getObjectives().stream().anyMatch(objective -> objective.getName().equals(OBJECTIVE));
    }

    private void schedule(long tick, Runnable action) {
        scheduler.schedule(tick, action);
    }

    void scheduleMenuSequence(long now, Runnable newerSlot, Runnable olderSlot,
                              Runnable close, Runnable reopen) {
        schedule(now + 6, () -> {
            newerSlot.run();
            executedSteps.record("menu.slot-update-newer");
        });
        schedule(now + 8, () -> {
            olderSlot.run();
            executedSteps.record("menu.slot-update-older");
        });
        schedule(now + 16, () -> {
            close.run();
            executedSteps.record("menu.close");
        });
        schedule(now + 24, () -> {
            reopen.run();
            executedSteps.record("menu.reopen");
        });
    }

    void tickAt(long tick) {
        scheduler.tick(tick);
    }

    void resetExecutionState() {
        scheduler.reset();
        executedSteps.reset();
    }

    private LabeledObservation executed(String label, String summary) {
        return new LabeledObservation(label, executedSteps.contains(label), summary);
    }

    private static boolean isSkyblockMenu(ItemStack stack) {
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && "SKYBLOCK_MENU".equals(customData.copyTag().getStringOr("id", ""));
    }

    private static ItemStack named(net.minecraft.world.item.Item item, String name) {
        var stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static void announce(ServerPlayer operator, List<LabeledObservation> observations) {
        if (operator == null) return;
        for (var observation : observations) {
            operator.sendSystemMessage(Component.literal("[" + observation.label() + "] "
                    + (observation.passed() ? "PASS " : "FAIL ") + observation.summary()));
        }
    }

}
