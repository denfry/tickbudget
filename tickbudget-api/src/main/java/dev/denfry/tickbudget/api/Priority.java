package dev.denfry.tickbudget.api;

/**
 * Scheduling priority of a budgeted task. Higher priorities receive a larger share of the surplus tick time pool.
 */
public enum Priority {
    /** Low priority, receiving smaller share of surplus time pool. */
    LOW,
    /** Default priority for tasks. */
    NORMAL,
    /** High priority, receiving larger share of surplus time pool. */
    HIGH,
    /** Ignores the shared pool: always runs up to its own budget, even when the tick is overloaded. */
    CRITICAL
}
