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
import org.bukkit.plugin.Plugin;

/**
 * Manages the queue and execution loop for budgeted tasks across platforms.
 */
public final class BudgetRunner {

    private final PlatformBridge bridge;
    private final TickBudgetConfig config;
    private final MetricsTracker metricsTracker;
    private final FairShareDispatcher dispatcher;
    private final SystemClock clock;

    private final List<BudgetedTaskHandle> allActiveTasks = new CopyOnWriteArrayList<>();
    private final List<BudgetedTaskHandle> paperSyncQueue = new CopyOnWriteArrayList<>();

    private volatile PlatformBridge.TaskCancellable paperTickLoopHandle;

    public BudgetRunner(
            PlatformBridge bridge,
            TickBudgetConfig config,
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

        if (bridge.isFolia() || handle.target() instanceof Target.OfAsync) {
            // On Folia, or for async tasks on Paper, schedule a repeating task directly on the target's scheduler
            PlatformBridge.TaskCancellable cancellable = bridge.scheduleRepeating(
                    handle.ownerPlugin(),
                    handle.target(),
                    () -> {
                        if (handle.state().isTerminal()) {
                            return;
                        }
                        long budget = handle.budget().nanosPerTick();
                        boolean hasMore = handle.executeSlice(budget, clock);
                        if (!hasMore) {
                            allActiveTasks.remove(handle);
                        }
                    },
                    1L
            );
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
        long poolNanos;
        if (elapsedNanos >= 0) {
            long remainingTickNanos = Math.max(0, totalTickNanos - elapsedNanos);
            poolNanos = Math.max(0, remainingTickNanos - config.safetyMarginNanos());
        } else {
            // Fallback when tick-start tracking is unavailable
            poolNanos = Math.max(0, totalTickNanos - config.safetyMarginNanos());
        }

        List<BudgetedTaskHandle> currentQueue = new ArrayList<>(paperSyncQueue);
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(currentQueue, poolNanos);

        List<BudgetedTaskHandle> scheduledTasks = new ArrayList<>();
        for (FairShareDispatcher.TaskAllocation allocation : allocations) {
            BudgetedTaskHandle task = allocation.handle();
            scheduledTasks.add(task);

            boolean hasMore = task.executeSlice(allocation.allocatedNanos(), clock);
            if (!hasMore) {
                paperSyncQueue.remove(task);
                allActiveTasks.remove(task);
            }
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
                allActiveTasks.remove(task);
                paperSyncQueue.remove(task);
            }
        }
    }

    public List<BudgetedTaskHandle> activeTasks() {
        return List.copyOf(allActiveTasks);
    }
}
