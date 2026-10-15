package com.coflnet.skyblock.testserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ScenarioResultWriter {
    private static final Gson CANONICAL_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private ScenarioResultWriter() {
    }

    public static void write(List<Result> results, String loadedFixtureDigest) {
        var root = new TreeMap<String, Object>();
        for (var property : ScenarioHostContract.RESULT_PROPERTIES.entrySet()) {
            String value = System.getProperty(property.getValue());
            root.put(property.getKey(), resultPropertyValue(property.getKey(), property.getValue(), value));
        }
        if (loadedFixtureDigest != null && !loadedFixtureDigest.startsWith("default:")
                && !root.get("fixture_sha256").equals(loadedFixtureDigest)) {
            throw new IllegalStateException("loaded fixture digest does not match the host binding");
        }
        root.put("passed", results.stream().allMatch(Result::passed));
        root.put("schema", 1);
        var scenarioDocuments = new ArrayList<Map<String, Object>>();
        for (var result : results) {
            var value = new TreeMap<String, Object>();
            value.put("id", result.id());
            value.put("passed", result.passed());
            value.put("summary", result.summary());
            scenarioDocuments.add(value);
        }
        root.put("scenarios", scenarioDocuments);
        byte[] canonical = (CANONICAL_GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        Path result = ScenarioHostContract.resultPath();
        Path temporary = result.resolveSibling(result.getFileName() + ".tmp");
        try {
            Files.write(temporary, canonical);
            Files.move(temporary, result, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("unable to write canonical scenario result", exception);
        }
    }

    private static Object resultPropertyValue(String key, String property, String value) {
        boolean emptySelection = key.equals("selected_scenario_id") && "".equals(value);
        if (value == null || (!emptySelection && value.isBlank()) || value.length() > 512) {
            throw new IllegalStateException("missing host-only property " + property);
        }
        if (!key.equals("attempt") && !key.equals("runtime_generation")) return value;
        try {
            int number = Integer.parseInt(value);
            if (number < 1) throw new NumberFormatException("must be positive");
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("invalid host-only property " + property, exception);
        }
    }

    public record Result(String id, boolean passed, String summary) {
        public Result {
            if (id == null || id.isBlank() || summary == null || summary.isBlank() || summary.length() > 512) {
                throw new IllegalArgumentException("result id/summary is missing or unbounded");
            }
        }

        public static Result from(String id, List<LabeledObservation> observations) {
            boolean passed = observations.stream().allMatch(LabeledObservation::passed);
            String labels = observations.stream().map(value -> value.label() + ":" + value.passed())
                    .reduce((a, b) -> a + "," + b).orElse("none");
            return new Result(id, passed, "labels=" + labels);
        }
    }
}
