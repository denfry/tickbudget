package dev.denfry.tickbudget.api;

import java.util.function.Consumer;

/**
 * Fluent builder for configuring and starting a budgeted task.
 */
public interface TaskBuilder {

    /**
     * Sets the per-tick budget limit for this task.
     *
     * @param budget the budget configuration
     * @return this builder
     */
    TaskBuilder budget(Budget budget);

    /**
     * Sets the per-tick budget limit in milliseconds.
     *
     * @param millisPerTick the time budget in milliseconds per tick
     * @return this builder
     */
    default TaskBuilder budgetMillis(double millisPerTick) {
        return budget(Budget.millisPerTick(millisPerTick));
    }

    /**
     * Sets the priority weight for fair-share scheduling. Default: {@link Priority#NORMAL}.
     *
     * @param priority the task priority
     * @return this builder
     */
    TaskBuilder priority(Priority priority);

    /**
     * Sets a human-readable name for this task shown in diagnostics and logs.
     *
     * @param name descriptive task name
     * @return this builder
     */
    TaskBuilder name(String name);

    /**
     * Sets a completion callback invoked after the task returns {@link StepResult#DONE}.
     *
     * @param callback the action to run on completion
     * @return this builder
     */
    TaskBuilder onComplete(Runnable callback);

    /**
     * Sets an error handler invoked if a step throws an uncaught exception.
     *
     * @param callback error handler receiving the exception
     * @return this builder
     */
    TaskBuilder onError(Consumer<Throwable> callback);

    /**
     * Queues and starts the task on the target scheduler.
     *
     * @return a handle for tracking, monitoring, or cancelling the task
     */
    TaskHandle start();
}
