package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.FairShareDispatcher;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FairShareDispatcherTest {

    private final TickBudgetConfig config = TickBudgetConfig.defaults();
    private final FakeClock clock = new FakeClock();
    private final FairShareDispatcher dispatcher = new FairShareDispatcher(() -> config, clock);

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

    private static long shareOf(List<FairShareDispatcher.PluginAllocation> allocations, MockPlugin plugin) {
        return allocations.stream()
                .filter(a -> a.plugin() == plugin)
                .findFirst()
                .orElseThrow()
                .shareNanos();
    }

    @Test
    @DisplayName("Floor guarantee: every plugin receives at least floor time when pool permits")
    void testFloorGuarantee() {
        MockPlugin p1 = new MockPlugin("PluginA");
        MockPlugin p2 = new MockPlugin("PluginB");

        BudgetedTaskHandle t1 = createTask("t1", p1, Priority.NORMAL, 5.0);
        BudgetedTaskHandle t2 = createTask("t2", p2, Priority.NORMAL, 5.0);

        long poolNanos = 10_000_000L;
        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(t1, t2), poolNanos);

        assertEquals(2, allocations.size());
        for (FairShareDispatcher.PluginAllocation alloc : allocations) {
            assertTrue(alloc.shareNanos() >= config.floorBudgetNanos(), "Share must be at least floor");
        }
        assertTrue(shareOf(allocations, p1) + shareOf(allocations, p2) <= poolNanos);
    }

    @Test
    @DisplayName("Higher priority plugins receive larger share of surplus time")
    void testPriorityWeighting() {
        MockPlugin p1 = new MockPlugin("PluginLow");
        MockPlugin p2 = new MockPlugin("PluginHigh");

        BudgetedTaskHandle tLow = createTask("tLow", p1, Priority.LOW, 10.0);
        BudgetedTaskHandle tHigh = createTask("tHigh", p2, Priority.HIGH, 10.0);

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(tLow, tHigh), 10_000_000L);

        assertTrue(shareOf(allocations, p2) > shareOf(allocations, p1), "HIGH priority must receive strictly more than LOW priority");
    }

    @Test
    @DisplayName("Overloaded tick still grants every plugin its floor")
    void testOverloadedTick() {
        MockPlugin p1 = new MockPlugin("PluginNorm");
        MockPlugin p2 = new MockPlugin("PluginCrit");

        BudgetedTaskHandle tNorm = createTask("tNorm", p1, Priority.NORMAL, 5.0);
        BudgetedTaskHandle tCrit = createTask("tCrit", p2, Priority.CRITICAL, 5.0);

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(tNorm, tCrit), 0L);

        assertEquals(config.floorBudgetNanos(), shareOf(allocations, p1));
        assertEquals(config.floorBudgetNanos(), shareOf(allocations, p2));
    }

    @Test
    @DisplayName("Plugin share is capped at the sum of its tasks' budgets")
    void testShareCappedAtDemand() {
        MockPlugin p1 = new MockPlugin("PluginSmall");
        BudgetedTaskHandle tSmall = createTask("tSmall", p1, Priority.HIGH, 0.2);

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(tSmall), 40_000_000L);

        assertEquals(tSmall.budget().nanosPerTick(), shareOf(allocations, p1), "Must be capped at task's budget");
    }

    @Test
    @DisplayName("Time a plugin cannot use is redistributed to the others")
    void testUnusedShareRedistributed() {
        MockPlugin small = new MockPlugin("Small");
        MockPlugin big = new MockPlugin("Big");

        BudgetedTaskHandle tSmall = createTask("tSmall", small, Priority.HIGH, 1.0);
        BudgetedTaskHandle tBig = createTask("tBig", big, Priority.LOW, 30.0);

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(tSmall, tBig), 20_000_000L);

        assertEquals(1_000_000L, shareOf(allocations, small));
        assertEquals(19_000_000L, shareOf(allocations, big));
    }

    @Test
    @DisplayName("Starting more tasks does not give a plugin a larger share")
    void testTaskCountDoesNotIncreaseShare() {
        MockPlugin many = new MockPlugin("Many");
        MockPlugin one = new MockPlugin("One");

        List<BudgetedTaskHandle> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(createTask("many-" + i, many, Priority.NORMAL, 10.0));
        }
        tasks.add(createTask("one", one, Priority.NORMAL, 10.0));

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(tasks, 10_000_000L);

        assertEquals(shareOf(allocations, one), shareOf(allocations, many));
    }

    @Test
    @DisplayName("Plugin tasks are ordered by priority, then by least recently run")
    void testTaskOrderWithinPlugin() {
        MockPlugin plugin = new MockPlugin("Plugin");

        BudgetedTaskHandle recent = createTask("recent", plugin, Priority.NORMAL, 1.0);
        BudgetedTaskHandle stale = createTask("stale", plugin, Priority.NORMAL, 1.0);
        BudgetedTaskHandle high = createTask("high", plugin, Priority.HIGH, 1.0);
        recent.markRun(5);
        stale.markRun(3);
        high.markRun(5);

        List<FairShareDispatcher.PluginAllocation> allocations = dispatcher.allocate(List.of(recent, stale, high), 40_000_000L);

        assertEquals(List.of(high, stale, recent), allocations.getFirst().tasks());
        assertEquals(3_000_000L, allocations.getFirst().shareNanos());
    }
}
