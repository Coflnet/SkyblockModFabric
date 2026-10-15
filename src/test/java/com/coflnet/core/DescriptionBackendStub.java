package com.coflnet.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

final class DescriptionBackendStub implements AutoCloseable {
    static final String CHEST_NAME = "Co-op Bazaar Orders";
    static final String ITEM_DESCRIPTION = "Order details:";
    static final String TOTAL_BUY = "Total buy: 75.05M";
    static final String TOTAL_SELL = "Total sell: 3.02M";
    static final int ORDER_SLOT = 13;
    static final int CLIENT_ITEM_COUNT = 90;

    private final HttpServer server;
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final AtomicReference<JsonObject> recordedRequest = new AtomicReference<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    DescriptionBackendStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/mod/description/modifications", this::handle);
    }

    void start() {
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    JsonObject awaitRequest() throws InterruptedException {
        if (!requestReceived.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("description request was not received within five seconds");
        }
        if (failure.get() != null) {
            throw new AssertionError("description stub rejected request", failure.get());
        }
        return recordedRequest.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                throw new AssertionError("expected POST, got " + exchange.getRequestMethod());
            }
            JsonObject request = JsonParser.parseString(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!CHEST_NAME.equals(request.get("chestName").getAsString())) {
                throw new AssertionError("unexpected chestName");
            }
            if (request.get("version").getAsInt() != 4) {
                throw new AssertionError("unexpected description version");
            }
            verifyInventoryNbt(request.get("fullInventoryNbt").getAsString());
            recordedRequest.set(request);
            writeResponse(exchange, 200, responseBody());
        } catch (Throwable throwable) {
            failure.set(throwable);
            writeResponse(exchange, 400, "[]");
        } finally {
            requestReceived.countDown();
        }
    }

    private static String responseBody() {
        var response = new StringBuilder("[");
        for (int slot = 0; slot < CLIENT_ITEM_COUNT; slot++) {
            if (slot > 0) response.append(',');
            if (slot == ORDER_SLOT) {
                response.append("[{\"type\":\"APPEND\",\"value\":\"")
                        .append(ITEM_DESCRIPTION).append("\",\"line\":0}]");
            } else {
                response.append("[]");
            }
        }
        return response.append(",[{\"type\":\"APPEND\",\"value\":\"")
                .append(TOTAL_BUY).append("\",\"line\":0},")
                .append("{\"type\":\"APPEND\",\"value\":\"\",\"line\":1},")
                .append("{\"type\":\"APPEND\",\"value\":\"")
                .append(TOTAL_SELL).append("\",\"line\":2},")
                .append("{\"type\":\"APPEND\",\"value\":\"\",\"line\":3},")
                .append("{\"type\":\"APPEND\",\"value\":\"")
                .append(ITEM_DESCRIPTION).append("\",\"line\":4}]]").toString();
    }

    static String orderInventoryNbt() throws IOException {
        var root = new CompoundTag();
        var order = new CompoundTag();
        order.putString("Tag", "AGATHA_COUPON");
        order.putString("Description", ITEM_DESCRIPTION);
        root.put("order", order);
        var output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static void verifyInventoryNbt(String encoded) throws IOException {
        if (encoded == null || encoded.isBlank()) {
            throw new AssertionError("fullInventoryNbt must be nonempty");
        }
        byte[] compressed = Base64.getDecoder().decode(encoded);
        CompoundTag inventory = NbtIo.readCompressed(
                new ByteArrayInputStream(compressed), NbtAccounter.create(1_048_576));
        if (!containsOrderMetadata(inventory)) {
            throw new AssertionError("fullInventoryNbt lacks Bazaar order Tag or Description");
        }
    }

    private static boolean containsOrderMetadata(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            if ("AGATHA_COUPON".equals(compound.getStringOr("Tag", ""))
                    && ITEM_DESCRIPTION.equals(compound.getStringOr("Description", ""))) {
                return true;
            }
            return compound.values().stream().anyMatch(DescriptionBackendStub::containsOrderMetadata);
        }
        if (tag instanceof ListTag list) {
            return list.stream().anyMatch(DescriptionBackendStub::containsOrderMetadata);
        }
        return false;
    }

    private static void writeResponse(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        try (var stub = new DescriptionBackendStub()) {
            stub.start();
            System.out.println("description-stub-ready " + stub.baseUrl());
            new CountDownLatch(1).await();
        }
    }
}
