package com.coflnet.skyblock.testserver;

import java.util.ArrayList;
import java.util.List;

final class AutomatedRunState {
    private int scenarioIndex;
    private long readyTick;
    private List<LabeledObservation> observations;

    void reset(long tick) {
        scenarioIndex = 0;
        readyTick = 0;
        observations = null;
    }

    int scenarioIndex() {
        return scenarioIndex;
    }

    boolean hasActiveScenario() {
        return observations != null;
    }

    void begin(List<LabeledObservation> setupObservations, long tick) {
        if (observations != null) throw new IllegalStateException("automated scenario already active");
        observations = new ArrayList<>(setupObservations);
        readyTick = tick + 32;
    }

    boolean readyToAssert(long tick, boolean pendingActions) {
        return observations != null && tick >= readyTick && !pendingActions;
    }

    ScenarioResultWriter.Result complete(String scenarioId, List<LabeledObservation> assertions) {
        if (observations == null) throw new IllegalStateException("no automated scenario is active");
        observations.addAll(assertions);
        var result = ScenarioResultWriter.Result.from(scenarioId, observations);
        observations = null;
        scenarioIndex++;
        return result;
    }

}
