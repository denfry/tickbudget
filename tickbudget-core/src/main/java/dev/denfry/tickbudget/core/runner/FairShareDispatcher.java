package dev.denfry.tickbudget.core.runner;

import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * Calculates fair-share budget slices for active tasks in a single domain tick.
 */
public final class FairShareDispatcher {

    private final TickBudgetConfig config;
    private final SystemClock clock;

    public FairShareDispatcher(TickBudgetConfig config, SystemClock clock) {
        this.config = config;
        this.clock = clock;
    }

    public record TaskAllocation(BudgetedTaskHandle handle, long allocatedNanos) {}

    /**
     * Calculates time allocations for ready tasks given the available tick budget pool.
     */
    public List<TaskAllocation> allocate(List<BudgetedTaskHandle> readyTasks, long availablePoolNanos) {
        if (readyTasks.isEmpty()) {
            return List.of();
        }

        // Group tasks by plugin, keeping the highest priority / oldest task first
        Map<Plugin, List<BudgetedTaskHandle>> tasksByPlugin = new HashMap<>();
        for (BudgetedTaskHandle task : readyTasks) {
            tasksByPlugin.computeIfAbsent(task.ownerPlugin(), k -> new ArrayList<>()).add(task);
        }

        // Sort tasks within each plugin by Priority (CRITICAL -> HIGH -> NORMAL -> LOW)
        for (List<BudgetedTaskHandle> list : tasksByPlugin.values()) {
            list.sort(Comparator.comparingInt((BudgetedTaskHandle t) -> config.weightFor(t.priority())).reversed());
        }

        List<TaskAllocation> allocations = new ArrayList<>();
        int activePluginCount = tasksByPlugin.size();

        if (availablePoolNanos <= 0) {
            // Overloaded tick: only CRITICAL tasks and guaranteed floor
            for (Map.Entry<Plugin, List<BudgetedTaskHandle>> entry : tasksByPlugin.entrySet()) {
                BudgetedTaskHandle task = entry.getValue().getFirst();
                if (task.priority() == Priority.CRITICAL) {
                    allocations.add(new TaskAllocation(task, task.budget().nanosPerTick()));
                } else {
                    allocations.add(new TaskAllocation(task, Math.min(config.floorBudgetNanos(), task.budget().nanosPerTick())));
                }
            }
            return allocations;
        }

        // 1. Reserve floor for each plugin
        long totalFloorRequired = (long) activePluginCount * config.floorBudgetNanos();
        long remainingPool;
        long floorPerPlugin;

        if (availablePoolNanos >= totalFloorRequired) {
            floorPerPlugin = config.floorBudgetNanos();
            remainingPool = availablePoolNanos - totalFloorRequired;
        } else {
            floorPerPlugin = availablePoolNanos / activePluginCount;
            remainingPool = 0;
        }

        // 2. Calculate priority weights for the top task of each plugin
        int totalWeight = 0;
        for (List<BudgetedTaskHandle> list : tasksByPlugin.values()) {
            BudgetedTaskHandle topTask = list.getFirst();
            totalWeight += config.weightFor(topTask.priority());
        }

        // 3. Allocate to each plugin's primary task
        for (Map.Entry<Plugin, List<BudgetedTaskHandle>> entry : tasksByPlugin.entrySet()) {
            BudgetedTaskHandle topTask = entry.getValue().getFirst();
            long baseFloor = floorPerPlugin;
            long weightShare = 0;
            if (remainingPool > 0 && totalWeight > 0) {
                int w = config.weightFor(topTask.priority());
                weightShare = (remainingPool * w) / totalWeight;
            }

            long totalForPlugin = baseFloor + weightShare;

            if (topTask.priority() == Priority.CRITICAL) {
                totalForPlugin = Math.max(totalForPlugin, topTask.budget().nanosPerTick());
            }

            // Cap allocation at the task's configured budget
            long taskBudget = topTask.budget().nanosPerTick();
            long finalAllocation = Math.min(totalForPlugin, taskBudget);

            allocations.add(new TaskAllocation(topTask, finalAllocation));
        }

        return allocations;
    }
}
