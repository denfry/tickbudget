package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PaperTickLoopTest {

    private final FakePlatformBridge bridge = new FakePlatformBridge();
    private final FakeClock clock = new FakeClock();
    private final TickBudgetConfig config = TickBudgetConfig.defaults();
    private final MetricsTracker metricsTracker = new MetricsTracker();
    private final BudgetRunner runner = new BudgetRunner(bridge, () -> config, metricsTracker, clock);
    private final TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, () -> config);

    private void startCounting(TickBudget tb, AtomicInteger steps, long stepMillis, double budgetMillis, Priority priority) {
        tb.run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceMillis(stepMillis);
            return StepResult.MORE;
        }).budget(Budget.millisPerTick(budgetMillis)).priority(priority).start();
    }

    @Test
    @DisplayName("Pool excludes the loop's own time from the previous tick duration")
    void testPoolExcludesOwnWork() {
        AtomicInteger steps = new AtomicInteger();
        startCounting(service.forPlugin(new MockPlugin("A")), steps, 1, 50.0, Priority.NORMAL);

        // 30 ms of server work -> 50 - 30 - 5 = 15 ms pool
        bridge.setLastTickDurationNanos(30_000_000L);
        runner.processPaperMainTick();
        assertEquals(15, steps.get());

        // The next tick reports 45 ms, but 15 ms of it was this loop: server work is still 30 ms
        bridge.setLastTickDurationNanos(45_000_000L);
        runner.processPaperMainTick();
        assertEquals(30, steps.get());
    }

    @Test
    @DisplayName("All tasks of one plugin progress in the same tick when its share allows")
    void testMultipleTasksPerPluginRun() {
        TickBudget tb = service.forPlugin(new MockPlugin("A"));
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        startCounting(tb, first, 1, 1.0, Priority.NORMAL);
        startCounting(tb, second, 1, 1.0, Priority.NORMAL);

        runner.processPaperMainTick();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    @DisplayName("Same-priority tasks of a plugin take turns when its share fits only one")
    void testRoundRobinWithinPlugin() {
        TickBudget tb = service.forPlugin(new MockPlugin("A"));
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        startCounting(tb, first, 1, 5.0, Priority.NORMAL);
        startCounting(tb, second, 1, 5.0, Priority.NORMAL);

        // Overloaded: only the 0.5 ms floor, which one 1 ms step exhausts
        bridge.setLastTickDurationNanos(60_000_000L);
        for (int i = 0; i < 4; i++) {
            runner.processPaperMainTick();
        }

        assertEquals(2, first.get());
        assertEquals(2, second.get());
    }

    @Test
    @DisplayName("Overshoot by one plugin is charged against the rest of the tick")
    void testOvershootConsumesTickBudget() {
        AtomicInteger slow = new AtomicInteger();
        AtomicInteger victim = new AtomicInteger();
        startCounting(service.forPlugin(new MockPlugin("Slow")), slow, 10, 1.0, Priority.NORMAL);
        startCounting(service.forPlugin(new MockPlugin("Victim")), victim, 1, 1.0, Priority.NORMAL);

        // Plugin order rotates, so over two ticks the slow plugin runs first exactly once
        runner.processPaperMainTick();
        runner.processPaperMainTick();

        assertEquals(2, slow.get());
        assertEquals(1, victim.get(), "The tick in which the slow step ran first has no time left for others");
        assertEquals(1, metricsTracker.get("Victim").deferredTicks());
    }

    @Test
    @DisplayName("CRITICAL tasks get their full budget even when the tick is overloaded")
    void testCriticalIgnoresOverload() {
        AtomicInteger critical = new AtomicInteger();
        AtomicInteger normal = new AtomicInteger();
        startCounting(service.forPlugin(new MockPlugin("Crit")), critical, 1, 5.0, Priority.CRITICAL);
        startCounting(service.forPlugin(new MockPlugin("Norm")), normal, 1, 5.0, Priority.NORMAL);

        bridge.setLastTickDurationNanos(60_000_000L);
        runner.processPaperMainTick();

        assertEquals(5, critical.get());
        assertTrue(normal.get() <= 1, "NORMAL gets at most its floor-sized slice");
    }
}
