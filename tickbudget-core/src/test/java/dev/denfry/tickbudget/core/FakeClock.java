package dev.denfry.tickbudget.core;

import java.util.concurrent.atomic.AtomicLong;

public class FakeClock implements SystemClock {

    private final AtomicLong currentTimeNanos;

    public FakeClock() {
        this(0L);
    }

    public FakeClock(long initialNanos) {
        this.currentTimeNanos = new AtomicLong(initialNanos);
    }

    @Override
    public long nanoTime() {
        return currentTimeNanos.get();
    }

    public void advanceNanos(long nanos) {
        currentTimeNanos.addAndGet(nanos);
    }

    public void advanceMillis(long millis) {
        advanceNanos(millis * 1_000_000L);
    }
}
