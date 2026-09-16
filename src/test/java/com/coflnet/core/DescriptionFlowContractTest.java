package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;
import CoflCore.classes.Position;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptionFlowContractTest {
    @AfterEach
    void resetEndpoint() {
        System.clearProperty(DescriptionEndpointOverride.PROPERTY);
        CoflCore.configuration.Config.BaseUrl = "https://sky.coflnet.com";
        DescriptionHandler.emptyTooltipData();
    }

    @Test
    void endpointOverrideIsExplicitAndLoopbackOnly() {
        CoflCore.configuration.Config.BaseUrl = "https://production-default.invalid";
        DescriptionEndpointOverride.applySystemProperty();
        assertEquals("https://production-default.invalid", CoflCore.configuration.Config.BaseUrl);

        System.setProperty(DescriptionEndpointOverride.PROPERTY, "https://example.com:443");
        assertThrows(IllegalArgumentException.class, DescriptionEndpointOverride::applySystemProperty);
        assertEquals("https://production-default.invalid", CoflCore.configuration.Config.BaseUrl);
    }

    @Test
    void immediateAndThrottledRequestsReturnWhileBackendIsStalled(@TempDir Path sessionDirectory)
            throws Exception {
        CoflCore.misc.SessionManager.setMainPath(sessionDirectory);
        for (long delayMs : new long[]{0, 50}) {
            var received = new CountDownLatch(1);
            var releaseResponse = new CountDownLatch(1);
            var returned = new CountDownLatch(1);
            var loaded = new CountDownLatch(1);
            var captured = new AtomicReference<JsonObject>();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/mod/description/modifications", exchange -> {
                try {
                    captured.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8)).getAsJsonObject());
                    received.countDown();
                    if (!releaseResponse.await(5, TimeUnit.SECONDS)) {
                        exchange.sendResponseHeaders(504, -1);
                        return;
                    }
                    byte[] response = "[[{\"type\":\"APPEND\",\"value\":\"loaded\",\"line\":0}],[]]"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            try {
                CoflCore.configuration.Config.BaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
                var request = new DescriptionRequest("Chest", new String[]{"outgoing-item"},
                        "outgoing-inventory", "scenario-user", new Position(12, 64, 34));
                Thread.startVirtualThread(() -> {
                    request.submit(delayMs, loaded::countDown);
                    returned.countDown();
                });
                assertTrue(received.await(2, TimeUnit.SECONDS), "request did not reach the backend");
                assertTrue(returned.await(1, TimeUnit.SECONDS), "caller blocked waiting for the response");
                assertEquals(1, loaded.getCount(), "completion ran before the response arrived");
                releaseResponse.countDown();
                assertTrue(loaded.await(2, TimeUnit.SECONDS), "response was not applied");
                assertEquals("Chest", captured.get().get("chestName").getAsString());
                assertEquals("outgoing-inventory", captured.get().get("fullInventoryNbt").getAsString());
                assertEquals(JsonParser.parseString("{\"x\":12,\"y\":64,\"z\":34}"), captured.get().get("position"));
                assertEquals("loaded", DescriptionHandler.getTooltipData("outgoing-item")[0].value);
            } finally {
                releaseResponse.countDown();
                server.stop(0);
            }
        }
    }

    @Test
    void failedUploadAllowsAnIdenticalOrderViewToBeRetried(@TempDir Path sessionDirectory) throws Exception {
        CoflCore.misc.SessionManager.setMainPath(sessionDirectory);
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/mod/description/modifications", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (attempts.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1);
            } else {
                byte[] response = "[[]]".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
            exchange.close();
        });
        server.start();
        try {
            CoflCore.configuration.Config.BaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var failed = new CountDownLatch(1);
            var loaded = new CountDownLatch(1);
            var request = new DescriptionRequest("Co-op Bazaar Orders", new String[]{"order"}, "same-inventory", "scenario-user", null);
            request.submit(0, loaded::countDown, failed::countDown);
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertEquals(1, loaded.getCount(), "failed upload must not report successful loading");
            request.submit(0, loaded::countDown, () -> {});
            assertTrue(loaded.await(3, TimeUnit.SECONDS));
            assertEquals(2, attempts.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void realDescriptionRequestMapsOrderDescriptionAndTrailingInfoDisplay(@TempDir Path sessionDirectory)
            throws Exception {
        try (var stub = new DescriptionBackendStub()) {
            stub.start();
            CoflCore.misc.SessionManager.setMainPath(sessionDirectory);
            System.setProperty(DescriptionEndpointOverride.PROPERTY, stub.baseUrl());
            DescriptionEndpointOverride.applySystemProperty();
            String[] visibleItems = new String[DescriptionBackendStub.CLIENT_ITEM_COUNT];
            Arrays.setAll(visibleItems, slot -> "EMPTY_SLOT_" + slot);
            String orderId = "BUY AGATHA COUPON OrderPrice per unit: 1,250 coins";
            visibleItems[DescriptionBackendStub.ORDER_SLOT] = orderId;
            String fullInventoryNbt = DescriptionBackendStub.orderInventoryNbt();

            DescriptionHandler.loadDescriptionForInventory(visibleItems, DescriptionBackendStub.CHEST_NAME,
                    fullInventoryNbt, "scenario-user");

            var request = stub.awaitRequest();
            assertEquals(DescriptionBackendStub.CHEST_NAME, request.get("chestName").getAsString());
            assertEquals(4, request.get("version").getAsInt());
            assertEquals(fullInventoryNbt, request.get("fullInventoryNbt").getAsString());
            assertEquals(DescriptionBackendStub.ITEM_DESCRIPTION,
                    DescriptionHandler.getTooltipData(orderId)[0].value);
            assertEquals(5, DescriptionHandler.getInfoDisplay().length);
            assertEquals(DescriptionBackendStub.TOTAL_BUY, DescriptionHandler.getInfoDisplay()[0].value);
            assertEquals("", DescriptionHandler.getInfoDisplay()[1].value);
            assertEquals(DescriptionBackendStub.TOTAL_SELL, DescriptionHandler.getInfoDisplay()[2].value);
            assertEquals("", DescriptionHandler.getInfoDisplay()[3].value);
            assertEquals(DescriptionBackendStub.ITEM_DESCRIPTION, DescriptionHandler.getInfoDisplay()[4].value);
        }
    }
}
