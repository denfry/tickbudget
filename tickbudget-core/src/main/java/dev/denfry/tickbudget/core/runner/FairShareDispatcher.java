package dev.denfry.tickbudget.core.runner;

import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

/**
 * Splits the tick time pool between plugins for a single domain tick.
 * <p>
 * Every plugin with ready tasks is guaranteed the floor budget (capped at its demand). The rest of the pool is
 * distributed by priority weight using max-min fairness: a plugin never receives more than its demand
 * (the sum of its tasks' budgets), and whatever it cannot use is redistributed to the others.
 */
public final class FairShareDispatcher {

    private final Supplier<TickBudgetConfig> configSupplier;
    private final SystemClock clock;

    public FairShareDispatcher(Supplier<TickBudgetConfig> configSupplier, SystemClock clock) {
        this.configSupplier = configSupplier;
        this.clock = clock;
    }

    /**
     * Time granted to one plugin for this tick.
     *
     * @param plugin the owning plugin
     * @param tasks the plugin's ready tasks in execution order: highest priority first, then least recently run
     * @param shareNanos nanoseconds the plugin's non-critical tasks may spend this tick
     */
    public record PluginAllocation(Plugin plugin, List<BudgetedTaskHandle> tasks, long shareNanos) {}

    /**
     * Calculates per-plugin shares for ready tasks given the available tick budget pool.
     * Plugins are returned in the order their first task appears in {@code readyTasks}.
     */
    public List<PluginAllocation> allocate(List<BudgetedTaskHandle> readyTasks, long availablePoolNanos) {
        if (readyTasks.isEmpty()) {
            return List.of();
        }
        TickBudgetConfig config = configSupplier.get();

        Map<Plugin, List<BudgetedTaskHandle>> tasksByPlugin = new LinkedHashMap<>();
        for (BudgetedTaskHandle task : readyTasks) {
            tasksByPlugin.computeIfAbsent(task.ownerPlugin(), k -> new ArrayList<>()).add(task);
        }

        Comparator<BudgetedTaskHandle> executionOrder = Comparator
                .comparingInt((BudgetedTaskHandle t) -> config.weightFor(t.priority())).reversed()
                .thenComparingLong(BudgetedTaskHandle::lastRunTick);

        int pluginCount = tasksByPlugin.size();
        List<Plugin> plugins = new ArrayList<>(tasksByPlugin.keySet());
        long[] demand = new long[pluginCount];
        long[] share = new long[pluginCount];
        int[] weight = new int[pluginCount];

        // 1. Guaranteed floor for every plugin, even when the tick is overloaded
        long remainingPool = Math.max(0L, availablePoolNanos);
        for (int i = 0; i < pluginCount; i++) {
            List<BudgetedTaskHandle> tasks = tasksByPlugin.get(plugins.get(i));
            tasks.sort(executionOrder);
            long pluginDemand = 0L;
            for (BudgetedTaskHandle task : tasks) {
                pluginDemand = saturatedAdd(pluginDemand, task.budget().nanosPerTick());
            }
            demand[i] = pluginDemand;
            weight[i] = Math.max(0, config.weightFor(tasks.getFirst().priority()));
            share[i] = Math.min(config.floorBudgetNanos(), pluginDemand);
            remainingPool -= share[i];
        }
        remainingPool = Math.max(0L, remainingPool);

        // 2. Weighted water-filling of the surplus until it is spent or every demand is met
        while (remainingPool > 0) {
            long totalWeight = 0L;
            for (int i = 0; i < pluginCount; i++) {
                if (share[i] < demand[i]) {
                    totalWeight += weight[i];
                }
            }
            if (totalWeight == 0L) {
                break;
            }

            long distributed = 0L;
            boolean anySaturated = false;
            for (int i = 0; i < pluginCount; i++) {
                if (share[i] >= demand[i] || weight[i] == 0) {
                    continue;
                }
                long offer = remainingPool * weight[i] / totalWeight;
                long granted = Math.min(offer, demand[i] - share[i]);
                share[i] += granted;
                distributed += granted;
                if (share[i] >= demand[i]) {
                    anySaturated = true;
                }
            }
            remainingPool -= distributed;
            // Without a newly satisfied plugin every offer was taken in full, so only rounding dust is left
            if (!anySaturated || distributed == 0L) {
                break;
            }
        }

        List<PluginAllocation> allocations = new ArrayList<>(pluginCount);
        for (int i = 0; i < pluginCount; i++) {
            Plugin plugin = plugins.get(i);
            allocations.add(new PluginAllocation(plugin, List.copyOf(tasksByPlugin.get(plugin)), share[i]));
        }
        return allocations;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }
}
