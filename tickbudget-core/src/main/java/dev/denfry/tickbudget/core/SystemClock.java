package dev.denfry.tickbudget.core;

/**
 * Clock abstraction for nanosecond time queries.
 * Enables deterministic testing with fake time.
 */
public interface SystemClock {

    long nanoTime();

    SystemClock DEFAULT = System::nanoTime;
}
