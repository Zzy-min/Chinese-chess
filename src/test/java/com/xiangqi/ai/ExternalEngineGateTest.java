package com.xiangqi.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalEngineGateTest {

    @Test
    void serializesConcurrentExternalEngineWork() throws Exception {
        ExternalEngineGate gate = new ExternalEngineGate(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < 3; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try (ExternalEngineGate.Lease lease = gate.tryAcquire(2_000L)) {
                        if (lease == null) {
                            return false;
                        }
                        Thread.sleep(40L);
                        return true;
                    }
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(3, TimeUnit.SECONDS));
            }
            assertEquals(1, gate.peakActiveCount());
            assertEquals(0, gate.activeCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timeoutDoesNotLeakPermit() throws Exception {
        ExternalEngineGate gate = new ExternalEngineGate(1);
        try (ExternalEngineGate.Lease ignored = gate.tryAcquire(10L)) {
            assertNull(gate.tryAcquire(20L));
            assertEquals(1, gate.activeCount());
        }
        try (ExternalEngineGate.Lease recovered = gate.tryAcquire(20L)) {
            assertTrue(recovered != null);
        }
        assertEquals(0, gate.activeCount());
    }
}
