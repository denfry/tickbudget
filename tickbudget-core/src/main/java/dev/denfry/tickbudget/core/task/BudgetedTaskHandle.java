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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class BudgetedTaskHandle implements TaskHandle {

    private static final Logger LOGGER = Logger.getLogger("TickBudget");
    private static final long VIOLATION_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final AtomicLong SEQUENCE = new AtomicLong();

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
    private final long sequence = SEQUENCE.incrementAndGet();
    // Guards against overlapping slices, e.g. an async timer firing while the previous run is still going
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile PlatformBridge.TaskCancellable platformTaskHandle;
    private volatile Runnable terminationListener;

    // Touched only by the thread holding `running`
    private boolean violationLogged;
    private long lastViolationLogNanos;
    private long suppressedViolations;

    // Paper main-thread tick loop only: tick number of the last slice, used for round-robin ordering
    private long lastRunTick;

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

    public PluginMetrics metrics() {
        return metrics;
    }

    /**
     * Submission order across all tasks; earlier tasks win ties in scheduling order.
     */
    public long sequence() {
        return sequence;
    }

    public long lastRunTick() {
        return lastRunTick;
    }

    public void markRun(long tick) {
        this.lastRunTick = tick;
    }

    /**
     * Attaches the platform scheduler task. If the task already terminated while it was being scheduled,
     * the platform task is cancelled immediately so it cannot keep repeating.
     */
    public void setPlatformTaskHandle(PlatformBridge.TaskCancellable handle) {
        this.platformTaskHandle = handle;
        if (handle != null && state.get().isTerminal()) {
            handle.cancel();
        }
    }

    /**
     * Sets a listener invoked once when the task reaches a terminal state, before callbacks and the future.
     */
    public void setTerminationListener(Runnable listener) {
        this.terminationListener = listener;
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
     * A call made while another slice of this task is still running returns immediately without stepping.
     */
    public boolean executeSlice(long allocatedNanos, SystemClock clock) {
        if (state.get() != TaskState.QUEUED || !running.compareAndSet(false, true)) {
            return state.get() == TaskState.QUEUED;
        }

        long budgetNanos = budget.nanosPerTick();
        long sliceSteps = 0L;
        long sliceNanos = 0L;
        long sliceViolations = 0L;
        long stepDuration = 0L;
        StepResult result = StepResult.MORE;
        Throwable failure = null;
        try {
            // One clock read per step: each step's end is the next step's start
            long now = clock.nanoTime();
            long deadline = now + allocatedNanos;
            while (now - deadline < 0 && state.get() == TaskState.QUEUED) {
                long stepStart = now;
                try {
                    result = task.step();
                } catch (Throwable t) {
                    failure = t;
                    break;
                }
                now = clock.nanoTime();
                stepDuration = now - stepStart;
                sliceSteps++;
                sliceNanos += stepDuration;
                if (stepDuration > budgetNanos) {
                    sliceViolations++;
                    reportViolation(stepDuration, now);
                }
                // DONE finishes the task, YIELD only ends this tick's slice
                if (result != StepResult.MORE) {
                    break;
                }
            }
        } finally {
            running.set(false);
        }

        // Metrics are flushed once per slice, before callbacks can observe them
        if (sliceSteps > 0) {
            steps.add(sliceSteps);
            totalNanos.add(sliceNanos);
            lastStepDurationNanos.set(stepDuration);
            metrics.recordSlice(sliceSteps, sliceNanos, sliceViolations);
        }

        if (failure != null) {
            fail(failure);
            return false;
        }
        if (result == StepResult.DONE) {
            complete();
            return false;
        }
        return state.get() == TaskState.QUEUED;
    }

    private void reportViolation(long stepDuration, long now) {
        if (violationLogged && now - lastViolationLogNanos < VIOLATION_LOG_INTERVAL_NANOS) {
            suppressedViolations++;
            return;
        }
        String suppressed = suppressedViolations > 0
                ? " (" + suppressedViolations + " similar warnings suppressed)"
                : "";
        LOGGER.warning(String.format(Locale.ROOT,
                "Task '%s' from plugin '%s' violated budget: step took %.3f ms (budget: %.3f ms)%s",
                name, ownerPlugin.getName(), stepDuration / 1_000_000.0, budget.nanosPerTick() / 1_000_000.0, suppressed));
        violationLogged = true;
        lastViolationLogNanos = now;
        suppressedViolations = 0;
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
                        "Exception in onComplete callback for task '" + name + "' (" + ownerPlugin.getName() + ")", t);
            }
            future.complete(null);
        }
    }

    public void fail(Throwable cause) {
        if (state.compareAndSet(TaskState.QUEUED, TaskState.FAILED)) {
            cleanUp();
            // An onError handler owns the reporting; log only when nobody else will
            if (onError == null) {
                LOGGER.log(Level.SEVERE,
                        "Task '" + name + "' from plugin '" + ownerPlugin.getName() + "' failed with exception:", cause);
            }
            try {
                if (onError != null) {
                    onError.accept(cause);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE,
                        "Exception in onError callback for task '" + name + "' (" + ownerPlugin.getName() + ")", t);
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
        Runnable listener = this.terminationListener;
        if (listener != null) {
            listener.run();
        }
    }
}
