package dev.denfry.tickbudget.api;

/**
 * Outcome of one {@link BudgetedTask#step()} execution slice.
 */
public enum StepResult {
    /** More work remains; the runner may invoke {@code step()} again if budget permits. */
    MORE,
    /** The task is completely finished. */
    DONE,
    /**
     * Nothing more to do in this tick, but the task is not finished: the slice ends and {@code step()} is called
     * again on a later tick. Use it for long-lived tasks that drain a queue which is sometimes empty, so an idle task
     * costs one cheap step per tick instead of spinning through its whole budget.
     *
     * @since 0.3.0
     */
    YIELD
}
