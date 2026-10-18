package com.coflnet.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FetchThrottleTest {
    @Test void oneFetchPerKeyAndInterval() {
        FetchThrottle throttle = new FetchThrottle(60_000);
        assertTrue(throttle.tryStart("A", 0));
        assertFalse(throttle.tryStart("A", 70_000), "still in flight");
        assertTrue(throttle.tryStart("B", 1), "keys are independent");
        throttle.finish("A");
        assertFalse(throttle.tryStart("A", 59_999), "cached for the interval");
        assertTrue(throttle.tryStart("A", 60_000));
    }
}
