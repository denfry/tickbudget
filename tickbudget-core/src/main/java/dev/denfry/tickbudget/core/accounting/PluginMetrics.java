package dev.denfry.tickbudget.core.accounting;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
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

    // Circular ring buffer of 60 one-second buckets. A bucket is replaced as a whole when its second
    // rolls over, so concurrent writers from several region threads never lose each other's time.
    private final AtomicReferenceArray<Bucket> secondBuckets = new AtomicReferenceArray<>(BUCKET_COUNT);

    private record Bucket(long second, LongAdder nanos) {}

    public PluginMetrics(String pluginName) {
        this.pluginName = pluginName;
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

    /**
     * Records the outcome of one execution slice.
     */
    public void recordSlice(long steps, long nanos, long violationCount) {
        totalSteps.add(steps);
        totalNanos.add(nanos);
        if (violationCount > 0) {
            violations.add(violationCount);
        }
        currentBucket().nanos().add(nanos);
    }

    private Bucket currentBucket() {
        long currentSec = System.currentTimeMillis() / 1000L;
        int idx = (int) (currentSec % BUCKET_COUNT);
        while (true) {
            Bucket bucket = secondBuckets.get(idx);
            if (bucket != null && bucket.second() == currentSec) {
                return bucket;
            }
            Bucket fresh = new Bucket(currentSec, new LongAdder());
            if (secondBuckets.compareAndSet(idx, bucket, fresh)) {
                return fresh;
            }
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
            Bucket bucket = secondBuckets.get(i);
            if (bucket != null && currentSec - bucket.second() < BUCKET_COUNT) {
                sum += bucket.nanos().sum();
            }
        }
        return sum;
    }

    public double millisLastMinute() {
        return nanosLastMinute() / 1_000_000.0;
    }
}
