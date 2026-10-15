package com.coflnet.skyblock.testserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioHostContractTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("skycofl.automated");
        System.clearProperty("coflnet.scenario.mode");
        System.clearProperty("coflnet.scenario.result");
        ScenarioHostContract.RESULT_PROPERTIES.values().forEach(System::clearProperty);
    }

    @Test
    void automatedModeArmsFromEitherOfficialLaunchProperty() {
        assertFalse(ScenarioHostContract.automated());
        System.setProperty("coflnet.scenario.mode", "automated");
        assertTrue(ScenarioHostContract.automated());

        System.clearProperty("coflnet.scenario.mode");
        System.setProperty("skycofl.automated", "true");
        assertTrue(ScenarioHostContract.automated());
    }

    @Test
    void resultUsesCanonicalHostPropertyNamesAndConfiguredPath() {
        assertEquals(Map.ofEntries(
                Map.entry("artifact_sha256", "coflnet.scenario.artifact_sha256"),
                Map.entry("attempt", "coflnet.scenario.attempt"),
                Map.entry("base_commit", "coflnet.scenario.base_commit"),
                Map.entry("fixture_sha256", "coflnet.scenario.fixture_sha256"),
                Map.entry("review_commit", "coflnet.scenario.review_commit"),
                Map.entry("review_seal_sha256", "coflnet.scenario.review_seal_sha256"),
                Map.entry("run_seal_sha256", "coflnet.scenario.run_seal_sha256"),
                Map.entry("runtime_generation", "coflnet.scenario.runtime_generation"),
                Map.entry("runtime_receipt_sha256", "coflnet.scenario.runtime_receipt_sha256"),
                Map.entry("runtime_sha256", "coflnet.scenario.runtime_sha256"),
                Map.entry("scenario_manifest_sha256", "coflnet.scenario.manifest_sha256"),
                Map.entry("selected_scenario_id", "coflnet.scenario.selected_id"),
                Map.entry("source_seal_sha256", "coflnet.scenario.source_seal_sha256"),
                Map.entry("target_branch", "coflnet.scenario.target_branch"),
                Map.entry("task_id", "coflnet.scenario.task_id")), ScenarioHostContract.RESULT_PROPERTIES);

        System.setProperty("coflnet.scenario.result", "/server/official-result.json");
        assertEquals(Path.of("/server/official-result.json"), ScenarioHostContract.resultPath());
    }

    @Test
    void resultWriterAllowsTheOfficialEmptyAllScenarioSelection(@TempDir Path temporaryDirectory) throws Exception {
        ScenarioHostContract.RESULT_PROPERTIES.forEach((key, property) ->
                System.setProperty(property, switch (key) {
                    case "selected_scenario_id" -> "";
                    case "attempt", "runtime_generation" -> "1";
                    default -> "host-value";
                }));
        Path result = temporaryDirectory.resolve("result.json");
        System.setProperty("coflnet.scenario.result", result.toString());

        ScenarioResultWriter.write(List.of(
                new ScenarioResultWriter.Result("world-signals", true, "labels=world.entities:true")), null);

        String document = Files.readString(result);
        assertTrue(document.contains("\"selected_scenario_id\":\"\""));
        assertTrue(document.contains("\"schema\":1"));
        assertTrue(document.contains("\"passed\":true"));
        assertTrue(document.contains("\"runtime_generation\":1"));
        assertTrue(document.contains("\"summary\":\"labels=world.entities:true\""));
    }
}
