package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskBuilder;
import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PluginGuardTest {

    private final FakePlatformBridge bridge = new FakePlatformBridge();
    private final FakeClock clock = new FakeClock();
    private final TickBudgetConfig config = TickBudgetConfig.defaults();
    private final MetricsTracker metricsTracker = new MetricsTracker();
    private final BudgetRunner runner = new BudgetRunner(bridge, () -> config, metricsTracker, clock);
    private final TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, () -> config);

    @Test
    @DisplayName("Requested budgets above max-task-budget are clamped and logged once per plugin")
    void testBudgetClampedToMax() {
        TickBudget tb = service.forPlugin(new MockPlugin("Greedy"));

        List<LogRecord> records = LogCapture.warnings(() -> {
            tb.run(Target.global(), () -> StepResult.MORE).budgetMillis(1_000.0).start();
            tb.run(Target.global(), () -> StepResult.MORE).budgetMillis(500.0).start();
        });

        for (var task : runner.activeTasks()) {
            assertEquals(config.maxTaskBudgetNanos(), task.budget().nanosPerTick());
        }
        assertEquals(1, records.size());
    }

    @Test
    @DisplayName("CRITICAL task with a huge budget cannot hold the overloaded tick past the cap")
    void testCriticalBoundedByCap() {
        AtomicInteger steps = new AtomicInteger();
        service.forPlugin(new MockPlugin("Crit")).run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceMillis(1);
            return StepResult.MORE;
        }).budget(Budget.millisPerTick(Double.POSITIVE_INFINITY)).priority(Priority.CRITICAL).start();

        bridge.setLastTickDurationNanos(60_000_000L);
        runner.processPaperMainTick();

        assertEquals(config.maxTaskBudgetNanos() / 1_000_000L, steps.get());
    }

    @Test
    @DisplayName("Default budget larger than the cap is clamped too")
    void testDefaultBudgetClamped() {
        TickBudgetConfig bigDefault = new TickBudgetConfig(
                config.safetyMarginNanos(),
                config.floorBudgetNanos(),
                100_000_000L,
                config.maxTaskBudgetNanos(),
                config.lowPriorityWeight(),
                config.normalPriorityWeight(),
                config.highPriorityWeight(),
                config.criticalPriorityWeight(),
                config.debugEnabled()
        );
        TickBudgetServiceImpl bigDefaultService = new TickBudgetServiceImpl(bridge, runner, metricsTracker, () -> bigDefault);

        bigDefaultService.forPlugin(new MockPlugin("A")).run(Target.global(), () -> StepResult.MORE).start();

        assertEquals(config.maxTaskBudgetNanos(), runner.activeTasks().getFirst().budget().nanosPerTick());
    }

    @Test
    @DisplayName("forPlugin rejects disabled plugins")
    void testForPluginRejectsDisabled() {
        MockPlugin plugin = new MockPlugin("Disabled");
        plugin.setEnabled(false);

        assertThrows(IllegalArgumentException.class, () -> service.forPlugin(plugin));
    }

    @Test
    @DisplayName("forPlugin rejects plugin objects not registered with the server")
    void testForPluginRejectsUnregistered() {
        bridge.setPluginsRegistered(false);

        assertThrows(IllegalArgumentException.class, () -> service.forPlugin(new MockPlugin("Impostor")));
    }

    @Test
    @DisplayName("Starting a task after the owner plugin is disabled fails without leaking it")
    void testStartRejectedForDisabledPlugin() {
        MockPlugin plugin = new MockPlugin("Leaving");
        TaskBuilder builder = service.forPlugin(plugin).run(Target.global(), () -> StepResult.MORE);
        plugin.setEnabled(false);

        assertThrows(IllegalStateException.class, builder::start);
        assertTrue(runner.activeTasks().isEmpty());
        assertEquals(0, metricsTracker.get("Leaving").activeTasks());
    }
}
