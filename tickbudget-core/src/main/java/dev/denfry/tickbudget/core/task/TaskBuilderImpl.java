package dev.denfry.tickbudget.core.task;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskBuilder;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import org.bukkit.plugin.Plugin;

public final class TaskBuilderImpl implements TaskBuilder {

    private static final AtomicLong TASK_COUNTER = new AtomicLong(1L);

    private final Plugin ownerPlugin;
    private final Target target;
    private final BudgetedTask task;
    private final long maxBudgetNanos;
    private final LongConsumer onBudgetClamped;
    private final PluginMetrics metrics;
    private final Function<BudgetedTaskHandle, TaskHandle> taskSubmitter;

    private Budget budget;
    private Priority priority = Priority.NORMAL;
    private String name;
    private Runnable onComplete;
    private Consumer<Throwable> onError;

    public TaskBuilderImpl(
            Plugin ownerPlugin,
            Target target,
            BudgetedTask task,
            long defaultBudgetNanos,
            long maxBudgetNanos,
            LongConsumer onBudgetClamped,
            PluginMetrics metrics,
            Function<BudgetedTaskHandle, TaskHandle> taskSubmitter
    ) {
        this.ownerPlugin = ownerPlugin;
        this.target = target;
        this.task = task;
        this.maxBudgetNanos = Math.max(1L, maxBudgetNanos);
        this.onBudgetClamped = onBudgetClamped;
        this.budget = new Budget(Math.max(1L, Math.min(defaultBudgetNanos, this.maxBudgetNanos)));
        this.metrics = metrics;
        this.taskSubmitter = taskSubmitter;
        this.name = ownerPlugin.getName() + "-task-" + TASK_COUNTER.getAndIncrement();
    }

    @Override
    public TaskBuilder budget(Budget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("budget cannot be null");
        }
        this.budget = budget;
        return this;
    }

    @Override
    public TaskBuilder priority(Priority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("priority cannot be null");
        }
        this.priority = priority;
        return this;
    }

    @Override
    public TaskBuilder name(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.name = name;
        return this;
    }

    @Override
    public TaskBuilder onComplete(Runnable callback) {
        this.onComplete = callback;
        return this;
    }

    @Override
    public TaskBuilder onError(Consumer<Throwable> callback) {
        this.onError = callback;
        return this;
    }

    @Override
    public TaskHandle start() {
        Budget effectiveBudget = budget;
        if (budget.nanosPerTick() > maxBudgetNanos) {
            onBudgetClamped.accept(budget.nanosPerTick());
            effectiveBudget = new Budget(maxBudgetNanos);
        }
        BudgetedTaskHandle handle = new BudgetedTaskHandle(
                name,
                ownerPlugin,
                target,
                task,
                effectiveBudget,
                priority,
                onComplete,
                onError,
                metrics
        );
        return taskSubmitter.apply(handle);
    }
}
