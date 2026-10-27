package dev.denfry.tickbudget.core.runner;

import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/**
 * Manages the queue and execution loop for budgeted tasks across platforms.
 */
public final class BudgetRunner {

    private final PlatformBridge bridge;
    private final Supplier<TickBudgetConfig> config;
    private final MetricsTracker metricsTracker;
    private final FairShareDispatcher dispatcher;
    private final SystemClock clock;

    private final List<BudgetedTaskHandle> allActiveTasks = new CopyOnWriteArrayList<>();
    private final List<BudgetedTaskHandle> paperSyncQueue = new CopyOnWriteArrayList<>();

    private volatile PlatformBridge.TaskCancellable paperTickLoopHandle;

    public BudgetRunner(
            PlatformBridge bridge,
            Supplier<TickBudgetConfig> config,
            MetricsTracker metricsTracker,
            SystemClock clock
    ) {
        this.bridge = bridge;
        this.config = config;
        this.metricsTracker = metricsTracker;
        this.clock = clock;
        this.dispatcher = new FairShareDispatcher(config, clock);
    }

    public void start(Plugin tickBudgetPlugin) {
        if (!bridge.isFolia()) {
            // On Paper, run a central tick dispatcher on the main thread
            this.paperTickLoopHandle = bridge.scheduleRepeating(
                    tickBudgetPlugin,
                    Target.global(),
                    this::processPaperMainTick,
                    () -> {},
                    1L
            );
        }
    }

    public void stop() {
        if (paperTickLoopHandle != null) {
            paperTickLoopHandle.cancel();
            paperTickLoopHandle = null;
        }
        for (BudgetedTaskHandle task : allActiveTasks) {
            task.cancel();
        }
        allActiveTasks.clear();
        paperSyncQueue.clear();
    }

    public TaskHandle submit(BudgetedTaskHandle handle) {
        allActiveTasks.add(handle);
        // Runs on every terminal transition (done, failed, cancelled, target retired)
        handle.setTerminationListener(() -> {
            allActiveTasks.remove(handle);
            paperSyncQueue.remove(handle);
        });

        if (bridge.isFolia() || handle.target() instanceof Target.OfAsync) {
            // On Folia, or for async tasks on Paper, schedule a repeating task directly on the target's scheduler
            PlatformBridge.TaskCancellable cancellable;
            try {
                cancellable = bridge.scheduleRepeating(
                        handle.ownerPlugin(),
                        handle.target(),
                        () -> {
                            if (!handle.state().isTerminal()) {
                                handle.executeSlice(handle.budget().nanosPerTick(), clock);
                            }
                        },
                        handle::cancel,
                        1L
                );
            } catch (RuntimeException e) {
                handle.cancel();
                throw e;
            }
            handle.setPlatformTaskHandle(cancellable);
        } else {
            // On Paper main thread: queue for the centralized fair-share tick loop
            paperSyncQueue.add(handle);
        }

        return handle;
    }

    /**
     * Executes one tick of the Paper main thread domain with fair-share scheduling.
     */
    public void processPaperMainTick() {
        if (paperSyncQueue.isEmpty()) {
            return;
        }

        // 50ms default tick window minus already-elapsed time in this tick, minus safety margin
        long totalTickNanos = 50_000_000L;
        long elapsedNanos = bridge.currentTickElapsedNanos();
        TickBudgetConfig cfg = config.get();
        long poolNanos;
        if (elapsedNanos >= 0) {
            long remainingTickNanos = Math.max(0, totalTickNanos - elapsedNanos);
            poolNanos = Math.max(0, remainingTickNanos - cfg.safetyMarginNanos());
        } else {
            // Fallback when tick-start tracking is unavailable
            poolNanos = Math.max(0, totalTickNanos - cfg.safetyMarginNanos());
        }

        List<BudgetedTaskHandle> currentQueue = new ArrayList<>(paperSyncQueue);
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(currentQueue, poolNanos);

        List<BudgetedTaskHandle> scheduledTasks = new ArrayList<>();
        for (FairShareDispatcher.TaskAllocation allocation : allocations) {
            BudgetedTaskHandle task = allocation.handle();
            scheduledTasks.add(task);

            task.executeSlice(allocation.allocatedNanos(), clock);
        }

        // Record deferred ticks for tasks that had to wait
        for (BudgetedTaskHandle task : currentQueue) {
            if (!scheduledTasks.contains(task)) {
                metricsTracker.getOrCreate(task.ownerPlugin().getName()).recordDeferredTick();
            }
        }
    }

    public void cancelTasksForPlugin(Plugin plugin) {
        for (BudgetedTaskHandle task : allActiveTasks) {
            if (task.ownerPlugin().equals(plugin)) {
                task.cancel();
            }
        }
    }

    public List<BudgetedTaskHandle> activeTasks() {
        return List.copyOf(allActiveTasks);
    }
}
