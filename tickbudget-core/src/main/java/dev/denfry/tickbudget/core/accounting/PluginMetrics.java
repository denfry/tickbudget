package dev.denfry.tickbudget.core.accounting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks performance and resource metrics for a single plugin.
 */
public final class PluginMetrics {

    private static final int BUCKET_COUNT = 60;

    private final String pluginName;
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final LongAdder totalSteps = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder deferredTicks = new LongAdder();
    private final LongAdder violations = new LongAdder();

    // Circular ring buffer of 60 one-second buckets
    private final AtomicLong[] secondBuckets = new AtomicLong[BUCKET_COUNT];
    private final AtomicLong[] bucketTimestamps = new AtomicLong[BUCKET_COUNT];

    public PluginMetrics(String pluginName) {
        this.pluginName = pluginName;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            secondBuckets[i] = new AtomicLong(0L);
            bucketTimestamps[i] = new AtomicLong(0L);
        }
    }

    public String pluginName() {
        return pluginName;
    }

    public void incrementActiveTasks() {
        activeTasks.incrementAndGet();
    }

    public void decrementActiveTasks() {
        activeTasks.updateAndGet(c -> Math.max(0, c - 1));
    }

    public int activeTasks() {
        return activeTasks.get();
    }

    public void recordStep(long durationNanos, boolean isViolation) {
        totalSteps.increment();
        totalNanos.add(durationNanos);
        if (isViolation) {
            violations.increment();
        }

        long currentSec = System.currentTimeMillis() / 1000L;
        int idx = (int) (currentSec % BUCKET_COUNT);
        long recordedSec = bucketTimestamps[idx].get();

        if (recordedSec != currentSec) {
            if (bucketTimestamps[idx].compareAndSet(recordedSec, currentSec)) {
                secondBuckets[idx].set(durationNanos);
            } else {
                secondBuckets[idx].addAndGet(durationNanos);
            }
        } else {
            secondBuckets[idx].addAndGet(durationNanos);
        }
    }

    public void recordDeferredTick() {
        deferredTicks.increment();
    }

    public long totalSteps() {
        return totalSteps.sum();
    }

    public long totalNanos() {
        return totalNanos.sum();
    }

    public long deferredTicks() {
        return deferredTicks.sum();
    }

    public long violations() {
        return violations.sum();
    }

    /**
     * Calculates total nanos consumed by this plugin across the last 60 seconds.
     */
    public long nanosLastMinute() {
        long currentSec = System.currentTimeMillis() / 1000L;
        long sum = 0L;
        for (int i = 0; i < BUCKET_COUNT; i++) {
            long ts = bucketTimestamps[i].get();
            if (currentSec - ts < BUCKET_COUNT) {
                sum += secondBuckets[i].get();
            }
        }
        return sum;
    }

    public double millisLastMinute() {
        return nanosLastMinute() / 1_000_000.0;
    }
}
