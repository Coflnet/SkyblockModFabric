package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
