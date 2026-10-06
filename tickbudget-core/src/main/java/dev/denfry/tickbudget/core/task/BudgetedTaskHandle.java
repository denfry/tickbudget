package dev.denfry.tickbudget.core.task;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.api.TaskState;
import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class BudgetedTaskHandle implements TaskHandle {

    private static final Logger LOGGER = Logger.getLogger("TickBudget");

    private final String name;
    private final Plugin ownerPlugin;
    private final Target target;
    private final BudgetedTask task;
    private final Budget budget;
    private final Priority priority;
    private final Runnable onComplete;
    private final Consumer<Throwable> onError;
    private final PluginMetrics metrics;

    private final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.QUEUED);
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final LongAdder steps = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong lastStepDurationNanos = new AtomicLong(0L);

    private volatile PlatformBridge.TaskCancellable platformTaskHandle;

    public BudgetedTaskHandle(
            String name,
            Plugin ownerPlugin,
            Target target,
            BudgetedTask task,
            Budget budget,
            Priority priority,
            Runnable onComplete,
            Consumer<Throwable> onError,
            PluginMetrics metrics
    ) {
        this.name = name;
        this.ownerPlugin = ownerPlugin;
        this.target = target;
        this.task = task;
        this.budget = budget;
        this.priority = priority;
        this.onComplete = onComplete;
        this.onError = onError;
        this.metrics = metrics;
        metrics.incrementActiveTasks();
    }

    @Override
    public String name() {
        return name;
    }

    public Plugin ownerPlugin() {
        return ownerPlugin;
    }

    public Target target() {
        return target;
    }

    public Budget budget() {
        return budget;
    }

    public Priority priority() {
        return priority;
    }

    @Override
    public TaskState state() {
        return state.get();
    }

    @Override
    public long steps() {
        return steps.sum();
    }

    public long totalNanos() {
        return totalNanos.sum();
    }

    public double totalMillis() {
        return totalNanos.sum() / 1_000_000.0;
    }

    public long lastStepDurationNanos() {
        return lastStepDurationNanos.get();
    }

    public void setPlatformTaskHandle(PlatformBridge.TaskCancellable handle) {
        this.platformTaskHandle = handle;
    }

    @Override
    public void cancel() {
        if (state.compareAndSet(TaskState.QUEUED, TaskState.CANCELLED)) {
            cleanUp();
            future.cancel(true);
        }
    }

    @Override
    public CompletableFuture<Void> future() {
        return future;
    }

    /**
     * Executes steps up to {@code allocatedNanos}.
     * Returns true if more steps remain for future ticks, false if the task is complete/terminated.
     */
    public boolean executeSlice(long allocatedNanos, SystemClock clock) {
        if (state.get() != TaskState.QUEUED) {
            return false;
        }

        long deadline = clock.nanoTime() + allocatedNanos;

        while (clock.nanoTime() < deadline) {
            if (state.get() != TaskState.QUEUED) {
                return false;
            }

            StepResult result;
            long stepStart = clock.nanoTime();
            try {
                result = task.step();
            } catch (Throwable t) {
                fail(t);
                return false;
            }
            long stepDuration = clock.nanoTime() - stepStart;
            lastStepDurationNanos.set(stepDuration);
            steps.increment();
            totalNanos.add(stepDuration);

            boolean isViolation = stepDuration > budget.nanosPerTick();
            metrics.recordStep(stepDuration, isViolation);
            if (isViolation) {
                LOGGER.log(Level.WARNING,
                        "[TickBudget] Task ''{0}'' from plugin ''{1}'' violated budget: step took {2} ms (budget: {3} ms)",
                        new Object[]{name, ownerPlugin.getName(), stepDuration / 1_000_000.0, budget.nanosPerTick() / 1_000_000.0});
            }

            if (result == StepResult.DONE) {
                complete();
                return false;
            }
        }

        return true;
    }

    private void complete() {
        if (state.compareAndSet(TaskState.QUEUED, TaskState.DONE)) {
            cleanUp();
            try {
                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE,
                        "[TickBudget] Exception in onComplete callback for task ''" + name + "'' (" + ownerPlugin.getName() + ")", t);
            }
            future.complete(null);
        }
    }

    public void fail(Throwable cause) {
        if (state.compareAndSet(TaskState.QUEUED, TaskState.FAILED)) {
            cleanUp();
            LOGGER.log(Level.SEVERE,
                    "[TickBudget] Task ''" + name + "'' from plugin ''" + ownerPlugin.getName() + "'' failed with exception:", cause);
            try {
                if (onError != null) {
                    onError.accept(cause);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE,
                        "[TickBudget] Exception in onError callback for task ''" + name + "'' (" + ownerPlugin.getName() + ")", t);
            }
            future.completeExceptionally(cause);
        }
    }

    private void cleanUp() {
        metrics.decrementActiveTasks();
        PlatformBridge.TaskCancellable h = this.platformTaskHandle;
        if (h != null) {
            h.cancel();
        }
    }
}
