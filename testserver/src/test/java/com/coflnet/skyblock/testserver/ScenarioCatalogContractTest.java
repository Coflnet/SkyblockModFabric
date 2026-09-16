package com.coflnet.skyblock.testserver;

import com.coflnet.core.MenuClassifier;
import com.coflnet.core.ScoreboardParser;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioCatalogContractTest {
    @AfterEach
    void clearScenarioSelection() {
        System.clearProperty("coflnet.scenario.mode");
        System.clearProperty("coflnet.scenario.selected_id");
    }

    @Test
    void indexOrderAndBranchMetadataAreStable() throws Exception {
        var catalog = ScenarioCatalog.load();
        assertEquals(List.of(
                        "ender-chest-sequence",
                        "bazaar-menu",
                        "bazaar-orders",
                        "auction-house-menu",
                        "trade-divider",
                        "world-signals"),
                catalog.scenarios().stream().map(Scenario::id).toList());
        assertTrue(catalog.scenarios().stream().allMatch(value -> value.manual() && value.automated()));

        try (var stream = getClass().getResourceAsStream("/fabric.mod.json")) {
            var metadata = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("coflnet_skyblock_scenarios", metadata.get("id").getAsString());
            assertEquals("server", metadata.get("environment").getAsString());
            assertEquals(1, metadata.getAsJsonObject("custom").get("coflnet_scenario_schema").getAsInt());
            assertEquals("main", metadata.getAsJsonObject("custom").get("coflnet_target_branch").getAsString());
            assertEquals("26.3", metadata.getAsJsonObject("depends").get("minecraft").getAsString());
            assertEquals("0.19.5", metadata.getAsJsonObject("depends").get("fabricloader").getAsString());
            assertEquals("0.160.6+26.3", metadata.getAsJsonObject("depends").get("fabric-api").getAsString());
        }
    }

    @Test
    void scenarioExpectationsUseProductionPureSeams() {
        assertTrue(MenuClassifier.isStorageChest("Ender Chest (1/9)"));
        assertFalse(MenuClassifier.isStorageChest("Bazaar ➜ Products"));
        assertTrue(MenuClassifier.isTradeMenu(45, "You     Scenario Partner",
                new boolean[]{true, true, true, true, true}));

        var values = ScoreboardParser.getRelevantLines(new String[]{
                ScenarioExpectations.PURSE_LINE, ScenarioExpectations.LOCATION_LINE});
        assertEquals(ScenarioExpectations.PURSE_LINE, values.left());
        assertEquals(ScenarioExpectations.LOCATION_LINE, values.right());
    }

    @Test
    void automatedRunUsesAllScenariosWhenSelectionIsEmpty() {
        var catalog = ScenarioCatalog.load();
        System.setProperty("coflnet.scenario.mode", "automated");

        var expected = catalog.scenarios();
        System.setProperty("coflnet.scenario.selected_id", "");
        assertEquals(expected, catalog.scenarios());
    }

    @Test
    void automatedRunUsesOnlySelectedScenario() {
        var catalog = ScenarioCatalog.load();
        var selected = catalog.require("bazaar-menu");
        System.setProperty("coflnet.scenario.mode", "automated");
        System.setProperty("coflnet.scenario.selected_id", "bazaar-menu");

        assertEquals(List.of(selected), catalog.scenarios());
    }

    @Test
    void manualJoinUsesExplicitSelectionAndDefaultsToFirstOwnedRoom() {
        var catalog = ScenarioCatalog.load();

        assertEquals("ender-chest-sequence", catalog.manualJoinScenario().id());
        System.setProperty("coflnet.scenario.selected_id", "bazaar-orders");
        assertEquals("bazaar-orders", catalog.manualJoinScenario().id());
    }

    @Test
    void resultSummariesExposeTheSameStableLabels() {
        var result = ScenarioResultWriter.Result.from("world-signals", List.of(
                new LabeledObservation("scoreboard.objective-team-score", true, "scoreboard changed"),
                new LabeledObservation("chunk.unload-reload", true, "chunk cycled")));
        assertTrue(result.passed());
        assertEquals("labels=scoreboard.objective-team-score:true,chunk.unload-reload:true", result.summary());
        assertThrows(IllegalArgumentException.class,
                () -> new LabeledObservation("Unstable label", true, "summary"));
    }
}
