package com.coflnet.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackgroundQueueTest {

    @Test
    void submitDoesNotBlockWhileWorkerIsStuck() throws InterruptedException {
        BackgroundQueue queue = new BackgroundQueue("test-nonblocking");
        try {
            CountDownLatch stuck = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            queue.submit(() -> {
                started.countDown();
                try {
                    stuck.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS), "worker never started the blocking task");

            CopyOnWriteArrayList<Integer> executed = new CopyOnWriteArrayList<>();
            long before = System.nanoTime();
            queue.submit(() -> executed.add(1));
            long elapsedMs = (System.nanoTime() - before) / 1_000_000;

            assertTrue(elapsedMs < 200, "submit() blocked for " + elapsedMs + "ms while the worker was stuck");

            stuck.countDown();
            waitUntil(() -> executed.size() == 1);
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void tasksRunInFifoOrder() throws InterruptedException {
        BackgroundQueue queue = new BackgroundQueue("test-fifo");
        try {
            CountDownLatch gate = new CountDownLatch(1);
            CopyOnWriteArrayList<Integer> executed = new CopyOnWriteArrayList<>();
            // Block the worker first so every subsequent submit() is guaranteed to
            // land in the queue before anything starts draining it.
            queue.submit(() -> {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            for (int i = 0; i < 10; i++) {
                int value = i;
                queue.submit(() -> executed.add(value));
            }
            gate.countDown();

            waitUntil(() -> executed.size() == 10);
            assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), executed);
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void overflowDropsOldestQueuedTask() throws InterruptedException {
        BackgroundQueue queue = new BackgroundQueue("test-overflow", 3);
        try {
            CountDownLatch gate = new CountDownLatch(1);
            CopyOnWriteArrayList<Integer> executed = new CopyOnWriteArrayList<>();
            CountDownLatch started = new CountDownLatch(1);
            queue.submit(() -> {
                started.countDown();
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));

            // Queue capacity is 3; submit 5 more tasks while the worker is stuck
            // on the blocking task above. The two oldest (1, 2) must be dropped.
            for (int i = 1; i <= 5; i++) {
                int value = i;
                queue.submit(() -> executed.add(value));
            }
            assertEquals(2, queue.droppedCount());

            gate.countDown();
            waitUntil(() -> executed.size() == 3);
            assertEquals(List.of(3, 4, 5), executed);
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void throwingTaskDoesNotKillTheWorker() throws InterruptedException {
        BackgroundQueue queue = new BackgroundQueue("test-throwing");
        try {
            CopyOnWriteArrayList<Integer> executed = new CopyOnWriteArrayList<>();
            queue.submit(() -> {
                throw new RuntimeException("boom");
            });
            queue.submit(() -> executed.add(1));

            waitUntil(() -> executed.size() == 1);
            assertEquals(List.of(1), executed);
        } finally {
            queue.shutdown();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition never became true within timeout");
    }
}
