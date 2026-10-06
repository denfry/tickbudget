package dev.denfry.tickbudget.api;

/** Outcome of one {@link BudgetedTask#step()} call. */
public enum StepResult {
    /** More work remains; the runner may call {@code step()} again. */
    MORE,
    /** The task finished. */
    DONE
}
