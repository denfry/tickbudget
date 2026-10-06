package dev.denfry.tickbudget.api;

/**
 * Outcome of one {@link BudgetedTask#step()} execution slice.
 */
public enum StepResult {
    /** More work remains; the runner may invoke {@code step()} again if budget permits. */
    MORE,
    /** The task is completely finished. */
    DONE
}
