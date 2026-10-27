package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskHandle;
import dev.denfry.tickbudget.api.TaskState;
import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskLifecycleTest {

    private final FakePlatformBridge bridge = new FakePlatformBridge();
    private final FakeClock clock = new FakeClock();
    private final AtomicReference<TickBudgetConfig> config = new AtomicReference<>(TickBudgetConfig.defaults());
    private final MetricsTracker metricsTracker = new MetricsTracker();
    private final BudgetRunner runner = new BudgetRunner(bridge, config::get, metricsTracker, clock);
    private final TickBudgetServiceImpl service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config::get);
    private final TickBudget tb = service.forPlugin(new MockPlugin("TestPlugin"));

    @Test
    @DisplayName("Cancelling a scheduler-driven task unregisters it and cancels the platform task")
    void testCancelUnregistersSchedulerTask() {
        bridge.setFolia(true);

        TaskHandle handle = tb.run(Target.global(), () -> StepResult.MORE).start();
        assertEquals(1, runner.activeTasks().size());

        handle.cancel();

        assertEquals(TaskState.CANCELLED, handle.state());
        assertTrue(runner.activeTasks().isEmpty(), "Cancelled task must not stay registered");
        assertTrue(bridge.repeating().getFirst().cancelled);
        assertEquals(0, metricsTracker.get("TestPlugin").activeTasks());
    }

    @Test
    @DisplayName("Task whose target is retired is cancelled and unregistered")
    void testRetiredTargetCancelsTask() {
        bridge.setFolia(true);

        TaskHandle handle = tb.run(Target.global(), () -> StepResult.MORE).start();
        bridge.repeating().getFirst().onRetired.run();

        assertEquals(TaskState.CANCELLED, handle.state());
        assertTrue(handle.future().isCancelled());
        assertTrue(runner.activeTasks().isEmpty());
    }

    @Test
    @DisplayName("Task finishing before its platform handle is attached still cancels the platform task")
    void testCompletionDuringSchedulingCancelsPlatformTask() {
        bridge.setRunRepeatingOnSchedule(true);

        TaskHandle handle = tb.run(Target.async(), () -> StepResult.DONE).start();

        assertEquals(TaskState.DONE, handle.state());
        assertTrue(bridge.repeating().getFirst().cancelled, "Repeating task must not outlive the finished task");
        assertTrue(runner.activeTasks().isEmpty());
    }

    @Test
    @DisplayName("Scheduler-driven task is unregistered after completing normally")
    void testSchedulerTaskUnregisteredOnCompletion() {
        bridge.setFolia(true);
        AtomicInteger steps = new AtomicInteger();

        TaskHandle handle = tb.run(Target.global(), () -> {
            clock.advanceMillis(1);
            return steps.incrementAndGet() >= 2 ? StepResult.DONE : StepResult.MORE;
        }).budget(Budget.millisPerTick(5.0)).start();

        bridge.repeating().getFirst().action.run();

        assertEquals(TaskState.DONE, handle.state());
        assertTrue(runner.activeTasks().isEmpty());
        assertTrue(bridge.repeating().getFirst().cancelled);
    }

    @Test
    @DisplayName("Tasks without an explicit budget use default-task-budget from the current config")
    void testDefaultBudgetFollowsConfig() {
        config.set(withSafetyMarginAndDefaultBudget(5_000_000L, 3_000_000L));

        tb.run(Target.global(), () -> StepResult.MORE).start();

        assertEquals(3_000_000L, runner.activeTasks().getFirst().budget().nanosPerTick());
    }

    @Test
    @DisplayName("Reloaded safety margin is applied by the running tick loop")
    void testReloadedSafetyMarginApplies() {
        AtomicInteger steps = new AtomicInteger();
        tb.run(Target.global(), () -> {
            steps.incrementAndGet();
            clock.advanceMillis(1);
            return StepResult.MORE;
        }).budget(Budget.millisPerTick(5.0)).start();

        // 40 ms already spent: 10 ms left, minus 5 ms margin -> 5 ms pool -> 5 one-millisecond steps
        bridge.setTickElapsedNanos(40_000_000L);
        runner.processPaperMainTick();
        assertEquals(5, steps.get());

        // 15 ms margin leaves no pool: only the 0.5 ms floor, i.e. a single step
        config.set(withSafetyMarginAndDefaultBudget(15_000_000L, 1_000_000L));
        runner.processPaperMainTick();
        assertEquals(6, steps.get());
    }

    @Test
    @DisplayName("Enabling debug at runtime makes assertOn log the violation")
    void testDebugToggleApplies() {
        bridge.setOwned(false);
        List<LogRecord> records = captureLogs(() -> {
            assertThrows(IllegalStateException.class, () -> tb.assertOn(Target.global()));
            config.set(config.get().withDebug(true));
            assertThrows(IllegalStateException.class, () -> tb.assertOn(Target.global()));
        });

        assertEquals(1, records.size(), "Only the assertion made with debug enabled is logged");
    }

    @Test
    @DisplayName("Budget violation warnings are rate limited per task")
    void testViolationLogRateLimited() {
        tb.run(Target.global(), () -> {
            clock.advanceMillis(2);
            return StepResult.MORE;
        }).budget(Budget.millisPerTick(1.0)).name("slow").start();

        List<LogRecord> records = captureLogs(() -> {
            for (int i = 0; i < 5; i++) {
                runner.processPaperMainTick();
            }
            clock.advanceMillis(10_000);
            runner.processPaperMainTick();
        });

        assertEquals(6, metricsTracker.get("TestPlugin").violations());
        assertEquals(2, records.size());
        assertEquals(" (4 similar warnings suppressed)", records.get(1).getParameters()[4]);
    }

    private static TickBudgetConfig withSafetyMarginAndDefaultBudget(long safetyMarginNanos, long defaultBudgetNanos) {
        TickBudgetConfig d = TickBudgetConfig.defaults();
        return new TickBudgetConfig(
                safetyMarginNanos,
                d.floorBudgetNanos(),
                defaultBudgetNanos,
                d.lowPriorityWeight(),
                d.normalPriorityWeight(),
                d.highPriorityWeight(),
                d.criticalPriorityWeight(),
                d.debugEnabled()
        );
    }

    private static List<LogRecord> captureLogs(Runnable action) {
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger("TickBudget");
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }
}
