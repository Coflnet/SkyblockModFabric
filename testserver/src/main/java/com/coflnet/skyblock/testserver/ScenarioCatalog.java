package com.coflnet.skyblock.testserver;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class ScenarioCatalog {
    private static final String INDEX = "/data/coflnet_scenarios/scenarios/index.json";
    private final List<Scenario> scenarios;

    private ScenarioCatalog(List<Scenario> scenarios) {
        this.scenarios = List.copyOf(scenarios);
    }

    public static ScenarioCatalog load() {
        try (var stream = ScenarioCatalog.class.getResourceAsStream(INDEX)) {
            if (stream == null) {
                throw new IllegalStateException("missing scenario index");
            }
            var document = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), IndexDocument.class);
            validate(document);
            return new ScenarioCatalog(document.scenarios());
        } catch (IOException | JsonParseException exception) {
            throw new IllegalStateException("invalid scenario index", exception);
        }
    }

    private static void validate(IndexDocument document) {
        if (document == null || document.schema() != 1 || document.scenarios() == null
                || document.scenarios().isEmpty() || document.scenarios().size() > 128) {
            throw new IllegalStateException("scenario index must use schema 1 and contain 1..128 entries");
        }
        var ids = new HashSet<String>();
        for (var scenario : document.scenarios()) {
            Objects.requireNonNull(scenario, "scenario");
            if (scenario.id() == null || !scenario.id().matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                    || scenario.title() == null || scenario.title().isBlank()
                    || !ids.add(scenario.id()) || scenario.room() == null
                    || Math.abs(scenario.room().x()) > 4096 || scenario.room().y() < 16
                    || scenario.room().y() > 240 || Math.abs(scenario.room().z()) > 4096
                    || (!scenario.manual() && !scenario.automated())) {
                throw new IllegalStateException("invalid or duplicate scenario: " + scenario.id());
            }
        }
    }

    public List<Scenario> scenarios() {
        String selectedId = ScenarioHostContract.selectedScenario();
        if (!ScenarioHostContract.automated() || selectedId == null || selectedId.isBlank()) return scenarios;
        return List.of(require(selectedId));
    }

    public Scenario require(String id) {
        return scenarios.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown scenario id: " + id));
    }

    public Scenario manualJoinScenario() {
        String selectedId = ScenarioHostContract.selectedScenario();
        if (selectedId != null && !selectedId.isBlank()) {
            Scenario selected = require(selectedId);
            if (!selected.manual()) {
                throw new IllegalArgumentException("selected scenario is not manual: " + selectedId);
            }
            return selected;
        }
        return scenarios.stream().filter(Scenario::manual).findFirst()
                .orElseThrow(() -> new IllegalStateException("scenario index has no manual scenario"));
    }

    public int indexOf(Scenario scenario) {
        return scenarios.indexOf(scenario);
    }

    private record IndexDocument(int schema, List<Scenario> scenarios) {
    }
}
