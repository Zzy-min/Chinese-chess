package com.xiangqi.ai;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Process-wide fair gate for memory-heavy native engines.
 */
public final class ExternalEngineGate {
    private static final Logger LOG = Logger.getLogger(ExternalEngineGate.class.getName());
    public static final long DEFAULT_WAIT_MILLIS = 8_000L;

    private static final ExternalEngineGate GLOBAL = new ExternalEngineGate(1);

    private final Semaphore permits;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peakActive = new AtomicInteger();

    public ExternalEngineGate(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be positive");
        }
        permits = new Semaphore(maxConcurrent, true);
    }

    public static ExternalEngineGate global() {
        return GLOBAL;
    }

    public Lease tryAcquire(long timeoutMillis) throws InterruptedException {
        if (!permits.tryAcquire(Math.max(0L, timeoutMillis), TimeUnit.MILLISECONDS)) {
            LOG.info(() -> "external_engine.gate timeoutMs=" + timeoutMillis + " active=" + active.get());
            return null;
        }
        int current = active.incrementAndGet();
        peakActive.accumulateAndGet(current, Math::max);
        LOG.info(() -> "external_engine.gate acquired active=" + current);
        return new Lease(this);
    }

    public int activeCount() {
        return active.get();
    }

    public int peakActiveCount() {
        return peakActive.get();
    }

    private void release() {
        int current = active.decrementAndGet();
        permits.release();
        LOG.info(() -> "external_engine.gate released active=" + current);
    }

    public static final class Lease implements AutoCloseable {
        private ExternalEngineGate owner;

        private Lease(ExternalEngineGate owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            ExternalEngineGate current = owner;
            if (current == null) {
                return;
            }
            owner = null;
            current.release();
        }
    }
}
