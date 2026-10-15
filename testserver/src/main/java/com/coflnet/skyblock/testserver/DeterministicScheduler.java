package com.coflnet.skyblock.testserver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DeterministicScheduler {
    private final List<ScheduledAction> actions = new ArrayList<>();
    private long generation;

    void reset() {
        generation++;
        actions.clear();
    }

    void schedule(long tick, Runnable action) {
        actions.add(new ScheduledAction(tick, generation, action));
    }

    void tick(long now) {
        var due = actions.stream().filter(action -> action.tick() <= now)
                .sorted(Comparator.comparingLong(ScheduledAction::tick))
                .toList();
        actions.removeAll(due);
        for (var action : due) {
            if (action.generation() == generation) action.action().run();
        }
    }

    boolean hasPendingActions() {
        return !actions.isEmpty();
    }

    private record ScheduledAction(long tick, long generation, Runnable action) {
    }
}
