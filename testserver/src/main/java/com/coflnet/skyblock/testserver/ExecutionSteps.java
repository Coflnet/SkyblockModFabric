package com.coflnet.skyblock.testserver;

import java.util.ArrayList;
import java.util.List;

final class ExecutionSteps {
    private final List<String> completed = new ArrayList<>();

    void reset() {
        completed.clear();
    }

    void record(String label) {
        if (!completed.contains(label)) completed.add(label);
    }

    boolean contains(String label) {
        return completed.contains(label);
    }

    boolean completedInOrder(List<String> expected) {
        int last = -1;
        for (String label : expected) {
            int position = completed.indexOf(label);
            if (position <= last) return false;
            last = position;
        }
        return true;
    }

    List<String> snapshot() {
        return List.copyOf(completed);
    }
}
