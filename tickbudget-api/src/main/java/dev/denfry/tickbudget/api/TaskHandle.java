package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a running or scheduled budgeted task.
 */
public interface TaskHandle {

    /**
     * Returns the configured name of this task.
     *
     * @return the task name
     */
    String name();

    /**
     * Returns the current lifecycle state of this task.
     *
     * @return the task state
     */
    TaskState state();

    /**
     * Returns the total number of {@code step()} invocations made so far.
     *
     * @return step count
     */
    long steps();

    /**
     * Cancels the task if it is not already in a terminal state.
     */
    void cancel();

    /**
     * Returns a future completing when this task terminates.
     * Completes normally on {@link TaskState#DONE}, exceptionally on {@link TaskState#FAILED},
     * and is cancelled on {@link #cancel()}.
     *
     * @return the completion future
     */
    CompletableFuture<Void> future();

    /**
     * Checks if this task has finished execution (done, cancelled, or failed).
     *
     * @return true if terminal state reached
     */
    default boolean isDone() {
        return state().isTerminal();
    }
}
