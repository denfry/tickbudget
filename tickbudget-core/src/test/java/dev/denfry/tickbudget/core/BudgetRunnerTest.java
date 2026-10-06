package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.api.TaskState;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BudgetRunnerTest {

    @Test
    @DisplayName("Task finishes when step returns DONE and triggers onComplete")
    void testTaskCompletion() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        AtomicInteger steps = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean(false);

        TaskHandle handle = tb.run(Target.global(), () -> {
            int cur = steps.incrementAndGet();
            clock.advanceMillis(1);
            return cur >= 3 ? StepResult.DONE : StepResult.MORE;
        })
        .budget(Budget.millisPerTick(5.0))
        .onComplete(() -> completed.set(true))
        .start();

        // 1st tick
        runner.processPaperMainTick();
        assertEquals(3, steps.get());
        assertTrue(completed.get());
        assertEquals(TaskState.DONE, handle.state());
        assertTrue(handle.isDone());
        assertTrue(handle.future().isDone());
    }

    @Test
    @DisplayName("Task stops within its budget and resumes next tick")
    void testBudgetPacing() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        AtomicInteger steps = new AtomicInteger();

        TaskHandle handle = tb.run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceMillis(1); // each step takes 1ms
            return steps.get() >= 5 ? StepResult.DONE : StepResult.MORE;
        })
        .budget(Budget.millisPerTick(2.0)) // 2 ms per tick max -> 2 steps per tick
        .start();

        // Tick 1
        runner.processPaperMainTick();
        assertEquals(2, steps.get());
        assertEquals(TaskState.QUEUED, handle.state());

        // Tick 2
        runner.processPaperMainTick();
        assertEquals(4, steps.get());
        assertEquals(TaskState.QUEUED, handle.state());

        // Tick 3
        runner.processPaperMainTick();
        assertEquals(5, steps.get());
        assertEquals(TaskState.DONE, handle.state());
    }

    @Test
    @DisplayName("Cancelling task stops execution and updates state")
    void testTaskCancel() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        AtomicInteger steps = new AtomicInteger();

        TaskHandle handle = tb.run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceMillis(1);
            return StepResult.MORE;
        })
        .budget(Budget.millisPerTick(1.0))
        .start();

        runner.processPaperMainTick();
        assertEquals(1, steps.get());

        handle.cancel();
        assertEquals(TaskState.CANCELLED, handle.state());
        assertTrue(handle.future().isCancelled());

        runner.processPaperMainTick();
        assertEquals(1, steps.get(), "No steps should run after cancel");
    }

    @Test
    @DisplayName("Exception in step fails task and invokes onError callback without crashing domain")
    void testTaskFailure() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        AtomicBoolean errorReported = new AtomicBoolean(false);

        TaskHandle handle = tb.run(Target.global(), () -> {
            throw new RuntimeException("Simulated failure");
        })
        .onError(t -> errorReported.set(true))
        .start();

        runner.processPaperMainTick();
        assertEquals(TaskState.FAILED, handle.state());
        assertTrue(errorReported.get());
        assertTrue(handle.future().isCompletedExceptionally());
    }

    @Test
    @DisplayName("Disabling plugin cancels all its active tasks")
    void testPluginDisableCancelsTasks() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        TaskHandle handle = tb.run(Target.global(), () -> StepResult.MORE)
                .budget(Budget.millisPerTick(2.0))
                .start();

        assertEquals(TaskState.QUEUED, handle.state());
        service.onPluginDisabled(plugin);

        assertEquals(TaskState.CANCELLED, handle.state());
    }

    @Test
    @DisplayName("assertOn throws IllegalStateException when target is not owned by current thread")
    void testAssertOn() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        bridge.setOwned(true);
        assertDoesNotThrow(() -> tb.assertOn(Target.global()));

        bridge.setOwned(false);
        assertThrows(IllegalStateException.class, () -> tb.assertOn(Target.global()));
    }

    @Test
    @DisplayName("Adaptive pool reduces available budget when server tick is already busy")
    void testAdaptivePoolUnderServerTickLoad() {
        FakePlatformBridge bridge = new FakePlatformBridge();
        FakeClock clock = new FakeClock();
        TickBudgetConfig config = TickBudgetConfig.defaults();
        MetricsTracker metricsTracker = new MetricsTracker();
        BudgetRunner runner = new BudgetRunner(bridge, config, metricsTracker, clock);

        MockPlugin plugin = new MockPlugin("TestPlugin");
        TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);
        var tb = service.forPlugin(plugin);

        AtomicInteger steps = new AtomicInteger();

        // Task configured with 5ms budget per tick, each step takes 0.6ms
        TaskHandle handle = tb.run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceNanos(600_000L); // 0.6 ms per step
            return steps.get() >= 10 ? StepResult.DONE : StepResult.MORE;
        })
        .budget(Budget.millisPerTick(5.0))
        .priority(Priority.NORMAL)
        .start();

        // Simulate server already spending 48ms in the tick before our tasks run
        // 50ms - 48ms = 2ms remaining; safety margin is 5ms -> pool is 0ms (overloaded tick)
        // Normal priority tasks will only receive floor budget (0.5ms)
        bridge.setTickElapsedNanos(48_000_000L);

        runner.processPaperMainTick();
        // Since floor budget is 0.5ms (500,000ns) and each step is 0.6ms,
        // after 1st step deadline is exceeded, so only 1 step executes in this overloaded tick
        assertEquals(1, steps.get(), "Under overloaded tick, task should only run within floor budget");
    }
}
