package com.coflnet.skyblock.testserver;

import java.nio.file.Path;
import java.util.Map;

final class ScenarioHostContract {
    private static final String PREFIX = "coflnet.scenario.";
    static final Map<String, String> RESULT_PROPERTIES = Map.ofEntries(
            Map.entry("artifact_sha256", PREFIX + "artifact_sha256"),
            Map.entry("attempt", PREFIX + "attempt"),
            Map.entry("base_commit", PREFIX + "base_commit"),
            Map.entry("fixture_sha256", PREFIX + "fixture_sha256"),
            Map.entry("review_commit", PREFIX + "review_commit"),
            Map.entry("review_seal_sha256", PREFIX + "review_seal_sha256"),
            Map.entry("run_seal_sha256", PREFIX + "run_seal_sha256"),
            Map.entry("runtime_generation", PREFIX + "runtime_generation"),
            Map.entry("runtime_receipt_sha256", PREFIX + "runtime_receipt_sha256"),
            Map.entry("runtime_sha256", PREFIX + "runtime_sha256"),
            Map.entry("scenario_manifest_sha256", PREFIX + "manifest_sha256"),
            Map.entry("selected_scenario_id", PREFIX + "selected_id"),
            Map.entry("source_seal_sha256", PREFIX + "source_seal_sha256"),
            Map.entry("target_branch", PREFIX + "target_branch"),
            Map.entry("task_id", PREFIX + "task_id"));

    private ScenarioHostContract() {
    }

    static boolean automated() {
        return Boolean.parseBoolean(System.getProperty("skycofl.automated", "false"))
                || "automated".equalsIgnoreCase(System.getProperty(PREFIX + "mode", ""));
    }

    static String selectedScenario() {
        return System.getProperty(PREFIX + "selected_id");
    }

    static boolean fixtureSelected() {
        return !"0".repeat(64).equals(System.getProperty(PREFIX + "fixture_sha256"));
    }

    static Path resultPath() {
        String configured = System.getProperty(PREFIX + "result");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("missing host-only property " + PREFIX + "result");
        }
        return Path.of(configured);
    }
}
