package com.coflnet.core;

import CoflCore.handlers.DescriptionHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DescriptionStateTest {
    private static final Gson GSON = new Gson();

    @BeforeEach
    @AfterEach
    void clear() {
        DescriptionState.setRefreshCallback(null);
        DescriptionState.reset();
    }

    private static DescriptionHandler.DescModification[] panel(String type, String value) {
        var modification = new JsonObject();
        modification.addProperty("type", type);
        modification.addProperty("value", value);
        return GSON.fromJson("[" + modification + "]", DescriptionHandler.DescModification[].class);
    }

    private static DescriptionRequest request(String title, String... ids) {
        return new DescriptionRequest(title, ids, "inventory", "test-user", null);
    }

    private static void applyPanel(String title, String value) {
        DescriptionState.apply(request(title), new DescriptionHandler.DescModification[][] {panel("APPEND", value)});
    }

    private static void assertPanel(String expected, DescriptionHandler.DescModification[] actual) {
        assertEquals(1, actual.length);
        assertEquals(expected, actual[0].value);
        assertEquals(expected, DescriptionState.panel()[0].value);
    }

    @Test
    void revisitingExactTitlesRestoresTheirPanelsAndFreshResponsesReplaceThem() {
        DescriptionState.openPanel("Your Bazaar Orders", new Object());
        applyPanel("Your Bazaar Orders", "old personal orders");
        DescriptionState.openPanel("Co-op Bazaar Orders", new Object());
        applyPanel("Co-op Bazaar Orders", "co-op orders");

        assertPanel("old personal orders", DescriptionState.openPanel("Your Bazaar Orders", new Object()));
        applyPanel("Your Bazaar Orders", "fresh personal orders");
        assertPanel("co-op orders", DescriptionState.openPanel("Co-op Bazaar Orders", new Object()));
        assertPanel("fresh personal orders", DescriptionState.openPanel("Your Bazaar Orders", new Object()));

        assertEquals(0, DescriptionState.openPanel("your Bazaar Orders", new Object()).length);
        assertEquals(0, DescriptionState.panel().length, "a cache miss must clear the previous title's panel");
    }

    @Test
    void authoritativeEmptyPanelEvictsThePreviousCachedPanel() {
        DescriptionState.openPanel("Chest", new Object());
        applyPanel("Chest", "old summary");
        DescriptionState.apply(request("Chest"), new DescriptionHandler.DescModification[][] {
                new DescriptionHandler.DescModification[0]});
        assertEquals(0, DescriptionState.panel().length);
        assertEquals(0, DescriptionState.openPanel("Chest", new Object()).length);
    }

    @Test
    void resetClearsCurrentPanelAndTitleCacheEvenForTheSameScreen() {
        Object screen = new Object();
        DescriptionState.openPanel("Chest", screen);
        applyPanel("Chest", "summary");
        DescriptionState.reset();
        assertEquals(0, DescriptionState.panel().length);
        assertEquals(0, DescriptionState.openPanel("Chest", screen).length);
        assertEquals(0, DescriptionState.openPanel("Chest", new Object()).length);
    }

    @Test
    void restoringAnInfoPanelDoesNotReplayCachedSlotTooltips() {
        DescriptionState.openPanel("Chest", new Object());
        DescriptionState.apply(request("Chest", "item"), new DescriptionHandler.DescModification[][] {
                panel("APPEND", "old item quote"), panel("APPEND", "chest summary")});
        assertNotNull(DescriptionHandler.getTooltipData("item"));
        DescriptionHandler.emptyTooltipData();

        assertPanel("chest summary", DescriptionState.openPanel("Chest", new Object()));
        assertTrue(DescriptionHandler.tooltipItemIdMap.isEmpty());
    }

    @Test
    void resizeKeepsFreshSuggestionButReopeningTheScreenNeverRestoresIt() {
        Object screen = new Object();
        DescriptionState.openPanel("Create BIN Auction", screen);
        applyPanel("Create BIN Auction", "previous summary");
        DescriptionState.apply(request("Create BIN Auction"), new DescriptionHandler.DescModification[][] {
                panel("SUGGEST", "Enter price: 1234")});
        assertPanel("Enter price: 1234", DescriptionState.openPanel("Create BIN Auction", screen));

        DescriptionState.closePanel(screen);
        assertEquals("Enter price: 1234", DescriptionState.panel()[0].value,
                "the sign opening from this menu still needs the live suggestion");
        assertEquals(0, DescriptionState.openPanel("Create BIN Auction", screen).length);
        assertEquals(0, DescriptionState.openPanel("Create BIN Auction", new Object()).length);
        assertEquals(0, DescriptionState.panel().length);
    }

    @Test
    void clickableFillAndCommandSuggestionsAreNotReplayedIncludingEscapedJson() {
        for (String action : new String[] {"fillsign:Enter amount: 64", "suggest:/bz Diamond",
                "/cofl bzorder 64 Diamond", "\\u0066illsign:Enter amount: 64"}) {
            DescriptionState.reset();
            Object screen = new Object();
            DescriptionState.openPanel("Bazaar", screen);
            applyPanel("Bazaar", "previous summary");
            String json = "[{\"text\":\"Next action\",\"onClick\":\"" + action + "\"}]";
            DescriptionState.apply(request("Bazaar"), new DescriptionHandler.DescModification[][] {panel("APPEND", json)});
            assertPanel(json, DescriptionState.openPanel("Bazaar", screen));
            assertEquals(0, DescriptionState.openPanel("Bazaar", new Object()).length, action);
        }
    }

    @Test
    void plainTextMentioningSuggestionActionsRemainsCacheable() {
        DescriptionState.openPanel("Help", new Object());
        applyPanel("Help", "The suggest: action offers a command without running it.");
        assertPanel("The suggest: action offers a command without running it.",
                DescriptionState.openPanel("Help", new Object()));
    }
}
