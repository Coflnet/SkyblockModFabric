package com.coflnet.core;

import CoflCore.classes.Position;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.misc.SessionManager;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DescriptionQueueTest {
    @AfterEach void clear() { DescriptionState.setRefreshCallback(null); DescriptionState.reset(); }

    @Test void bazaarSlotBurstUploadsTheFinalInventoryOnce(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            backend.releaseFirst.countDown();
            var queue = new DescriptionQueue(Runnable::run, 0);
            var debounce = new ChestChangeDebouncer(150, 600);
            String title = "Co-op Bazaar Orders";
            assertFalse(MenuClassifier.shouldLoadDescriptionsOnOpen(title, true));
            var slots = new String[]{"empty", "empty", "empty"};
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = "order-" + slot;
                if (ChestChangeDebouncer.needsReload(slot, 90, true, false)
                        && MenuClassifier.shouldReloadOnChestChange(title, slots[slot]))
                    debounce.markChanged(slot * 50L);
                assertTrue(debounce.poll(slot * 50L) > 0);
                assertTrue(backend.uploads.isEmpty(), "Partial inventories must not start network requests");
            }
            assertEquals(0, debounce.poll(250));
            var applied = new CountDownLatch(1);
            queue.submit(backend.request(title, String.join(",", slots), null), response -> {
                applied.countDown(); return true;
            }, () -> fail("final offer refresh failed"));
            assertTrue(applied.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("order-0,order-1,order-2"), backend.uploads);
            assertEquals(-1, debounce.poll(300));
        }
    }

    @Test void confirmedEmptyOrderViewStillUploadsAfterWaitingForContents(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            backend.releaseFirst.countDown();
            var queue = new DescriptionQueue(Runnable::run, 0);
            var debounce = new ChestChangeDebouncer(150, 600);
            String title = "Your Bazaar Orders";
            assertFalse(MenuClassifier.shouldLoadDescriptionsOnOpen(title, true));
            assertTrue(backend.uploads.isEmpty());
            // The complete content packet is authoritative even when the container remains empty.
            debounce.markChanged(0);
            assertEquals(0, debounce.poll(150));
            var applied = new CountDownLatch(1);
            queue.submit(backend.request(title, "confirmed-empty", null), response -> {
                applied.countDown(); return true;
            }, () -> fail("empty order confirmation failed"));
            assertTrue(applied.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("confirmed-empty"), backend.uploads);
        }
    }

    @Test void populatedOrderReinitializationConfirmsTheSameInventoryAgain(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            backend.releaseFirst.countDown();
            var queue = new DescriptionQueue(Runnable::run, 0);
            var debounce = new ChestChangeDebouncer(150, 600);
            for (int open = 0; open < 2; open++) {
                String title = "Co-op Bazaar Orders";
                assertTrue(MenuClassifier.shouldLoadDescriptionsOnOpen(title, false));
                queue.invalidateDeduplication();
                debounce.markChanged(open * 1000L);
                assertEquals(0, debounce.poll(open * 1000L + 150));
                var applied = new CountDownLatch(1);
                queue.submit(backend.request(title, "populated-order-view", null), response -> {
                    applied.countDown(); return true;
                }, () -> fail("populated view refresh failed"));
                assertTrue(applied.await(2, TimeUnit.SECONDS));
            }
            assertEquals(List.of("populated-order-view", "populated-order-view"), backend.uploads);
        }
    }

    @Test void obsoleteResponseCannotReplaceTooltipPanelOrSuggestion(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            var dispatched = new LinkedBlockingQueue<Runnable>();
            var queue = new DescriptionQueue(dispatched::add, 0);
            var old = backend.request("old", null);
            queue.submit(old, response -> { DescriptionState.apply(old, response); return true; }, () -> fail("old fetch failed"));
            assertTrue(backend.firstReceived.await(2, TimeUnit.SECONDS));
            var newer = backend.request("new", null);
            queue.submit(newer, response -> { DescriptionState.apply(newer, response); return true; }, () -> fail("new fetch failed"));
            backend.releaseFirst.countDown();
            Runnable oldApply = dispatched.poll(2, TimeUnit.SECONDS);
            Runnable newApply = dispatched.poll(2, TimeUnit.SECONDS);
            assertNotNull(oldApply);
            assertNotNull(newApply);
            newApply.run();
            oldApply.run();
            assertEquals("new", DescriptionHandler.getTooltipData("item")[0].value);
            assertEquals("Enter price: new", DescriptionState.panel()[0].value);
        }
    }

    @Test void resetFencesInFlightAndQueuedSnapshotsAndAllowsIdenticalInventory(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            var queue = new DescriptionQueue(Runnable::run, 0);
            var applied = new CopyOnWriteArrayList<String>();
            var finished = new CountDownLatch(1);
            queue.submit(backend.request("same", null), response -> applied.add("old"), () -> {});
            assertTrue(backend.firstReceived.await(2, TimeUnit.SECONDS));
            queue.submit(backend.request("queued-old-session", new Position(2, 3, 4)), response -> applied.add("queued"), () -> {});
            queue.reset();
            queue.submit(backend.request("same", null), response -> { applied.add("new"); finished.countDown(); return true; }, () -> {});
            backend.releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("new"), applied);
            assertEquals(List.of("same", "same"), backend.uploads);
        }
    }

    @Test void burstCoalescesLatestSnapshotButPreservesOtherStoragePositions(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            var queue = new DescriptionQueue(Runnable::run, 0);
            var finished = new CountDownLatch(1);
            queue.submit(backend.request("first", new Position(1, 2, 3)), response -> true, () -> {});
            assertTrue(backend.firstReceived.await(2, TimeUnit.SECONDS));
            for (int change = 0; change < 100; change++)
                queue.submit(backend.request("change-" + change, new Position(1, 2, 3)), response -> true, () -> {});
            queue.submit(backend.request("other-chest", new Position(4, 5, 6)), response -> { finished.countDown(); return true; }, () -> {});
            backend.releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first", "change-99", "other-chest"), backend.uploads);
        }
    }

    @Test void queueRejectsOverflowExplicitlyWithoutDroppingAcceptedStorage(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            var queue = new DescriptionQueue(Runnable::run, 0);
            queue.submit(backend.request("first", null), response -> true, () -> {});
            assertTrue(backend.firstReceived.await(2, TimeUnit.SECONDS));
            for (int chest = 0; chest < 256; chest++)
                assertTrue(queue.submit(backend.request("stored", new Position(chest, 1, 1)), response -> true, () -> {}));
            var rejected = new CountDownLatch(1);
            assertFalse(queue.submit(backend.request("overflow", new Position(300, 1, 1)), response -> true, rejected::countDown));
            assertEquals(0, rejected.getCount());
            queue.reset();
            var finished = new CountDownLatch(1);
            queue.submit(backend.request("after-reset", null), response -> { finished.countDown(); return true; }, () -> {});
            backend.releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first", "after-reset"), backend.uploads);
        }
    }

    @Test void reconnectRefreshSurvivesCoalescingWithNewerInventory(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            var queue = new DescriptionQueue(Runnable::run, 0);
            var finished = new CountDownLatch(1);
            queue.submit(backend.request("in-flight", null), response -> true, () -> {});
            assertTrue(backend.firstReceived.await(2, TimeUnit.SECONDS));
            queue.submit(backend.request("login", null).withGoalRefresh(), response -> true, () -> {});
            queue.submit(backend.request("newer", null), response -> { finished.countDown(); return true; }, () -> {});
            backend.releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("in-flight", "newer"), backend.uploads);
            assertEquals(List.of(false, true), backend.goalRefreshes);
        }
    }

    @Test void malformedResponseNeverPartiallyChangesDisplayedState(@TempDir Path directory) throws Exception {
        try (var backend = new Backend(directory)) {
            backend.releaseFirst.countDown();
            var request = backend.request("valid", null);
            DescriptionState.apply(request, request.fetch());
            for (String invalid : new String[]{"null", "[]", "[[null]]", "[[{}]]"}) {
                backend.response = invalid;
                assertThrows(RuntimeException.class, request::fetch);
                assertEquals("valid", DescriptionHandler.getTooltipData("item")[0].value);
                assertEquals("Enter price: valid", DescriptionState.panel()[0].value);
            }
        }
    }

    @Test void resetClearsTheCurrentPanelAndKeepsItsListener() {
        var updates = new java.util.concurrent.atomic.AtomicInteger();
        DescriptionState.setRefreshCallback((ids, title) -> updates.incrementAndGet());
        DescriptionState.reset();
        assertEquals(1, updates.get());
        var request = new DescriptionRequest("Chest", new String[0], "inventory", "test-user", null);
        DescriptionState.apply(request, new DescriptionHandler.DescModification[0][]);
        assertEquals(2, updates.get());
        assertEquals(0, DescriptionState.panel().length);
    }

    @Test void identicalItemsInDifferentContextsAreDifferentSnapshots() {
        var request = new DescriptionRequest("Chest", new String[]{"item"}, "nbt", "user", new Position(1, 2, 3), "skyblock", "url");
        assertTrue(request.sameSnapshot(new DescriptionRequest("Chest", new String[]{"item"}, "nbt", "user", new Position(1, 2, 3), "skyblock", "url")));
        assertFalse(request.sameSnapshot(new DescriptionRequest("Backpack", new String[]{"item"}, "nbt", "user", new Position(1, 2, 3), "skyblock", "url")));
        assertFalse(request.sameSnapshot(new DescriptionRequest("Chest", new String[]{"item"}, "nbt", "user", new Position(2, 2, 3), "skyblock", "url")));
        assertFalse(request.sameSnapshot(new DescriptionRequest("Chest", new String[]{"item"}, "nbt", "other-user", new Position(1, 2, 3), "skyblock", "url")));
        assertFalse(request.sameSnapshot(new DescriptionRequest("Chest", new String[]{"item"}, "nbt", "user", new Position(1, 2, 3), "other-server", "url")));
    }

    private static final class Backend implements AutoCloseable {
        final HttpServer server;
        final CountDownLatch firstReceived = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final List<String> uploads = new CopyOnWriteArrayList<>();
        final List<Boolean> goalRefreshes = new CopyOnWriteArrayList<>();
        volatile String response;

        Backend(Path directory) throws Exception {
            SessionManager.setMainPath(directory);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/mod/description/modifications", exchange -> {
                try {
                    var body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    String nbt = body.get("fullInventoryNbt").getAsString();
                    uploads.add(nbt);
                    goalRefreshes.add(body.has("forceGoalRefresh") && body.get("forceGoalRefresh").getAsBoolean());
                    firstReceived.countDown();
                    if (uploads.size() == 1 && !releaseFirst.await(5, TimeUnit.SECONDS)) {
                        exchange.sendResponseHeaders(504, -1);
                        return;
                    }
                    String json = response == null ? "[[{\"type\":\"APPEND\",\"value\":" + new Gson().toJson(nbt)
                            + "}],[{\"type\":\"SUGGEST\",\"value\":" + new Gson().toJson("Enter price: " + nbt) + "}]]" : response;
                    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally { exchange.close(); }
            });
            server.start();
        }

        DescriptionRequest request(String inventory, Position position) {
            return request("Chest", inventory, position);
        }

        DescriptionRequest request(String title, String inventory, Position position) {
            return new DescriptionRequest(title, new String[]{"item"}, inventory, "test-user", position,
                    "skyblock", "http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override public void close() { releaseFirst.countDown(); server.stop(0); }
    }
}
