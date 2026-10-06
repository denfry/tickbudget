package dev.denfry.tickbudget.api;

/** Scheduling priority of a budgeted task. Higher priorities get a larger share of the free tick time. */
public enum Priority {
    LOW,
    NORMAL,
    HIGH,
    /** Ignores the shared pool: always runs up to its own budget, even when the tick is overloaded. */
    CRITICAL
}
