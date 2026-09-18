package com.coflnet.core;

import CoflCore.configuration.Config;
import CoflCore.handlers.DescriptionHandler;
import CoflCore.misc.SessionManager;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LorePreviewTest {
    @Test void sendsDraftOnlyWithRequestAndNeverReplacesLiveTooltips(@TempDir Path sessions) throws Exception {
        SessionManager.setMainPath(sessions);
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/mod/description/modifications", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "[[],[{\"type\":\"APPEND\",\"value\":\"Draft preview\",\"line\":0}],[]]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String previousUrl = Config.BaseUrl;
        var existing = new DescriptionHandler.DescModification[0];
        DescriptionHandler.tooltipItemIdMap.put("preview-test-live", existing);
        try {
            Config.BaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            var settings = JsonParser.parseString("{\"Fields\":[],\"Disabled\":false}").getAsJsonObject();
            var result = LorePreview.load("item-nbt", "preview-player", settings, 1);
            assertEquals("Draft preview", result[0].value);
            var request = JsonParser.parseString(captured.get()).getAsJsonObject();
            assertEquals(settings, request.get("settings"));
            assertEquals("item-nbt", request.get("fullInventoryNbt").getAsString());
            assertEquals("Lore preview", request.get("chestName").getAsString());
            assertSame(existing, DescriptionHandler.getTooltipData("preview-test-live"));
        } finally {
            Config.BaseUrl = previousUrl;
            DescriptionHandler.tooltipItemIdMap.remove("preview-test-live");
            server.stop(0);
        }
    }
}
