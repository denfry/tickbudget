package dev.denfry.tickbudget.api;

import java.util.function.Consumer;

public interface TaskBuilder {

    /** Per-tick budget of this task. Default: 1 ms. */
    TaskBuilder budget(Budget budget);

    default TaskBuilder budgetMillis(double millisPerTick) {
        return budget(Budget.millisPerTick(millisPerTick));
    }

    /** Default: {@link Priority#NORMAL}. */
    TaskBuilder priority(Priority priority);

    /** Label shown in {@code /tickbudget top} and in error logs. */
    TaskBuilder name(String name);

    /** Called on the task's own thread after the last step. */
    TaskBuilder onComplete(Runnable callback);

    /** Called on the task's own thread when a step throws. The task is removed afterwards. */
    TaskBuilder onError(Consumer<Throwable> callback);

    /** Queues the task. */
    TaskHandle start();
}
