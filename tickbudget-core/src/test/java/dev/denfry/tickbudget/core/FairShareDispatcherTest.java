package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.FairShareDispatcher;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FairShareDispatcherTest {

    private final TickBudgetConfig config = TickBudgetConfig.defaults();
    private final FakeClock clock = new FakeClock();
    private final FairShareDispatcher dispatcher = new FairShareDispatcher(config, clock);

    private BudgetedTaskHandle createTask(String name, MockPlugin plugin, Priority priority, double budgetMillis) {
        return new BudgetedTaskHandle(
                name,
                plugin,
                Target.global(),
                () -> StepResult.MORE,
                Budget.millisPerTick(budgetMillis),
                priority,
                null,
                null,
                new PluginMetrics(plugin.getName())
        );
    }

    @Test
    @DisplayName("Floor guarantee: every plugin receives at least floor time when pool permits")
    void testFloorGuarantee() {
        MockPlugin p1 = new MockPlugin("PluginA");
        MockPlugin p2 = new MockPlugin("PluginB");

        BudgetedTaskHandle t1 = createTask("t1", p1, Priority.NORMAL, 5.0);
        BudgetedTaskHandle t2 = createTask("t2", p2, Priority.NORMAL, 5.0);

        // Pool has 10 ms available
        long poolNanos = 10_000_000L;
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(List.of(t1, t2), poolNanos);

        assertEquals(2, allocations.size());
        for (FairShareDispatcher.TaskAllocation alloc : allocations) {
            assertTrue(alloc.allocatedNanos() >= config.floorBudgetNanos(),
                    "Allocation must be at least floor");
        }
    }

    @Test
    @DisplayName("Higher priority tasks receive larger share of surplus time")
    void testPriorityWeighting() {
        MockPlugin p1 = new MockPlugin("PluginLow");
        MockPlugin p2 = new MockPlugin("PluginHigh");

        BudgetedTaskHandle tLow = createTask("tLow", p1, Priority.LOW, 10.0);
        BudgetedTaskHandle tHigh = createTask("tHigh", p2, Priority.HIGH, 10.0);

        long poolNanos = 10_000_000L;
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(List.of(tLow, tHigh), poolNanos);

        long allocLow = 0;
        long allocHigh = 0;
        for (FairShareDispatcher.TaskAllocation alloc : allocations) {
            if (alloc.handle() == tLow) allocLow = alloc.allocatedNanos();
            if (alloc.handle() == tHigh) allocHigh = alloc.allocatedNanos();
        }

        assertTrue(allocHigh > allocLow, "HIGH priority must receive strictly more than LOW priority");
    }

    @Test
    @DisplayName("Overloaded tick executes CRITICAL tasks and floor only")
    void testOverloadedTick() {
        MockPlugin p1 = new MockPlugin("PluginNorm");
        MockPlugin p2 = new MockPlugin("PluginCrit");

        BudgetedTaskHandle tNorm = createTask("tNorm", p1, Priority.NORMAL, 5.0);
        BudgetedTaskHandle tCrit = createTask("tCrit", p2, Priority.CRITICAL, 5.0);

        long poolNanos = 0L; // completely overloaded
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(List.of(tNorm, tCrit), poolNanos);

        assertEquals(2, allocations.size());
        for (FairShareDispatcher.TaskAllocation alloc : allocations) {
            if (alloc.handle() == tCrit) {
                assertEquals(tCrit.budget().nanosPerTick(), alloc.allocatedNanos(), "CRITICAL must get full budget");
            } else {
                assertEquals(config.floorBudgetNanos(), alloc.allocatedNanos(), "NORMAL should get floor");
            }
        }
    }

    @Test
    @DisplayName("Allocated time is capped at the task's own configured budget")
    void testAllocationCappedAtTaskBudget() {
        MockPlugin p1 = new MockPlugin("PluginSmall");
        BudgetedTaskHandle tSmall = createTask("tSmall", p1, Priority.HIGH, 0.2); // only 0.2 ms budget

        long poolNanos = 40_000_000L; // 40 ms pool available
        List<FairShareDispatcher.TaskAllocation> allocations = dispatcher.allocate(List.of(tSmall), poolNanos);

        assertEquals(1, allocations.size());
        assertEquals(tSmall.budget().nanosPerTick(), allocations.getFirst().allocatedNanos(),
                "Must be capped at task's budget");
    }
}
