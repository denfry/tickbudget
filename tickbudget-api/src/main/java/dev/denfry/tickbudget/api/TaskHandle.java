package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;

public interface TaskHandle {

    String name();

    TaskState state();

    /** Number of {@code step()} calls made so far. */
    long steps();

    /** Cancels the task. No effect once it is finished. */
    void cancel();

    /** Completes normally on {@code DONE}, exceptionally on failure, and is cancelled on {@link #cancel()}. */
    CompletableFuture<Void> future();

    default boolean isDone() {
        return state().isTerminal();
    }
}
