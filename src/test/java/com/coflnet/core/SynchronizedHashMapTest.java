package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynchronizedHashMapTest {

    @Test
    void basicOperationsBehaveLikeAHashMap() {
        SynchronizedHashMap<String, Integer> map = new SynchronizedHashMap<>();
        assertNull(map.put("a", 1));
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(2, map.getOrDefault("a", -1));
        assertEquals(-1, map.getOrDefault("missing", -1));
        assertTrue(map.containsKey("a"));
        assertEquals(1, map.size());

        map.putAll(Map.of("b", 3, "c", 4));
        assertEquals(3, map.size());

        assertEquals(2, map.remove("a"));
        assertFalse(map.containsKey("a"));

        map.clear();
        assertEquals(0, map.size());
    }

    @Test
    void concurrentPutAndGetSmokeTest() throws InterruptedException {
        SynchronizedHashMap<Integer, Integer> map = new SynchronizedHashMap<>();
        int threads = 8;
        int perThread = 500;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException ignored) {
                }
                for (int i = 0; i < perThread; i++) {
                    int key = base + i;
                    map.put(key, key);
                    map.get(key);
                }
                done.countDown();
            });
            thread.setDaemon(true);
            thread.start();
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "concurrent writers did not finish in time");

        assertEquals(threads * perThread, map.size());
        for (int i = 0; i < threads * perThread; i++) {
            assertEquals(i, map.get(i));
        }
    }
}
