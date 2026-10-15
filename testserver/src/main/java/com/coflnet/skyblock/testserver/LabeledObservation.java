package com.coflnet.skyblock.testserver;

public record LabeledObservation(String label, boolean passed, String summary) {
    public LabeledObservation {
        if (label == null || !label.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) {
            throw new IllegalArgumentException("unstable observation label");
        }
        if (summary == null || summary.isBlank() || summary.length() > 240) {
            throw new IllegalArgumentException("summary must contain 1..240 characters");
        }
    }
}
