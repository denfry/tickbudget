package dev.denfry.tickbudget.core.runner;

import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/**
 * Manages the queue and execution loop for budgeted tasks across platforms.
 */
public final class BudgetRunner {

    private static final long TICK_NANOS = 50_000_000L;

    private final PlatformBridge bridge;
    private final Supplier<TickBudgetConfig> config;
    private final FairShareDispatcher dispatcher;
    private final SystemClock clock;

    // Hash sets: tasks are added and removed constantly, so copy-on-write lists would cost O(n) per change
    private final Set<BudgetedTaskHandle> allActiveTasks = ConcurrentHashMap.newKeySet();
    private final Set<BudgetedTaskHandle> paperSyncQueue = ConcurrentHashMap.newKeySet();

    private volatile PlatformBridge.TaskCancellable paperTickLoopHandle;

    // Paper main-thread tick loop state
    private long tickCounter;
    private long lastLoopNanos;

    public BudgetRunner(
            PlatformBridge bridge,
            Supplier<TickBudgetConfig> config,
            SystemClock clock
    ) {
        this.bridge = bridge;
        this.config = config;
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
        if (!handle.ownerPlugin().isEnabled()) {
            handle.cancel();
            throw new IllegalStateException(
                    "Cannot start task '" + handle.name() + "': plugin '" + handle.ownerPlugin().getName() + "' is disabled");
        }
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
     * <p>
     * The pool is the tick window left after the server's own work in the previous tick (its duration minus the
     * time this loop spent in it) and the safety margin. Plugins run in an order that rotates every tick; time
     * actually used, including overshoot, is charged both to the plugin's share and to the tick total, so one
     * slow step cannot push the tick past what was planned. CRITICAL tasks always get their own budget.
     */
    public void processPaperMainTick() {
        if (paperSyncQueue.isEmpty()) {
            lastLoopNanos = 0L;
            return;
        }
        long loopStart = clock.nanoTime();
        tickCounter++;

        TickBudgetConfig cfg = config.get();
        long lastTickNanos = bridge.lastTickDurationNanos();
        long serverWorkNanos = lastTickNanos >= 0 ? Math.max(0L, lastTickNanos - lastLoopNanos) : 0L;
        long poolNanos = Math.max(0L, TICK_NANOS - serverWorkNanos - cfg.safetyMarginNanos());

        List<BudgetedTaskHandle> currentQueue = new ArrayList<>(paperSyncQueue);
        currentQueue.sort(Comparator.comparingLong(BudgetedTaskHandle::sequence));
        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(currentQueue, poolNanos);

        long plannedNanos = 0L;
        for (FairShareDispatcher.PluginAllocation allocation : allocations) {
            plannedNanos += allocation.shareNanos();
        }

        Set<BudgetedTaskHandle> ranThisTick = new HashSet<>();
        long spentNanos = 0L;
        int pluginCount = allocations.size();
        int offset = (int) (tickCounter % pluginCount);
        for (int i = 0; i < pluginCount; i++) {
            FairShareDispatcher.PluginAllocation allocation = allocations.get((offset + i) % pluginCount);
            long pluginRemaining = allocation.shareNanos();
            for (BudgetedTaskHandle task : allocation.tasks()) {
                long slice = task.priority() == Priority.CRITICAL
                        ? task.budget().nanosPerTick()
                        : Math.min(task.budget().nanosPerTick(), Math.min(pluginRemaining, plannedNanos - spentNanos));
                if (slice <= 0L) {
                    continue;
                }
                long sliceStart = clock.nanoTime();
                task.markRun(tickCounter);
                task.executeSlice(slice, clock);
                long used = clock.nanoTime() - sliceStart;
                pluginRemaining -= used;
                spentNanos += used;
                ranThisTick.add(task);
            }
        }

        // Record deferred ticks for tasks that had to wait
        for (BudgetedTaskHandle task : currentQueue) {
            if (!ranThisTick.contains(task)) {
                task.metrics().recordDeferredTick();
            }
        }
        lastLoopNanos = clock.nanoTime() - loopStart;
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
