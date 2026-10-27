package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Budget;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Priority;
import dev.denfry.tickbudget.api.StepResult;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskState;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HotPathTest {

    private final FakeClock clock = new FakeClock();
    private final MockPlugin plugin = new MockPlugin("TestPlugin");

    private BudgetedTaskHandle handle(BudgetedTask task, Consumer<Throwable> onError, PluginMetrics metrics) {
        return new BudgetedTaskHandle("task", plugin, Target.global(), task, Budget.millisPerTick(5.0),
                Priority.NORMAL, null, onError, metrics);
    }

    @Test
    @DisplayName("A slice started while another slice of the same task runs does not step")
    void testReentrantSliceSkipped() {
        AtomicReference<BudgetedTaskHandle> self = new AtomicReference<>();
        AtomicInteger steps = new AtomicInteger();
        AtomicBoolean nestedResult = new AtomicBoolean();
        BudgetedTaskHandle h = handle(() -> {
            steps.incrementAndGet();
            if (steps.get() == 1) {
                nestedResult.set(self.get().executeSlice(1_000_000L, clock));
            }
            clock.advanceMillis(1);
            return steps.get() >= 2 ? StepResult.DONE : StepResult.MORE;
        }, null, new PluginMetrics("TestPlugin"));
        self.set(h);

        h.executeSlice(5_000_000L, clock);

        assertTrue(nestedResult.get(), "Nested call reports the task as still running");
        assertEquals(2, steps.get(), "Nested call must not execute steps of its own");
        assertEquals(TaskState.DONE, h.state());
    }

    @Test
    @DisplayName("Slice metrics are flushed before onComplete observes the handle")
    void testMetricsVisibleInCallbacks() {
        PluginMetrics metrics = new PluginMetrics("TestPlugin");
        AtomicInteger counter = new AtomicInteger();
        BudgetedTaskHandle h = handle(() -> {
            clock.advanceMillis(1);
            return counter.incrementAndGet() >= 3 ? StepResult.DONE : StepResult.MORE;
        }, null, metrics);

        h.executeSlice(10_000_000L, clock);

        assertEquals(3, h.steps());
        assertEquals(3_000_000L, h.totalNanos());
        assertEquals(1_000_000L, h.lastStepDurationNanos());
        assertEquals(3, metrics.totalSteps());
        assertEquals(3_000_000L, metrics.nanosLastMinute());
    }

    @Test
    @DisplayName("Failure is logged by TickBudget only when no onError handler is set")
    void testFailureLoggedOnlyWithoutHandler() {
        List<LogRecord> withHandler = LogCapture.records(Level.SEVERE, () ->
                handle(() -> {
                    throw new IllegalStateException("boom");
                }, error -> {}, new PluginMetrics("TestPlugin")).executeSlice(1_000_000L, clock));
        List<LogRecord> withoutHandler = LogCapture.records(Level.SEVERE, () ->
                handle(() -> {
                    throw new IllegalStateException("boom");
                }, null, new PluginMetrics("TestPlugin")).executeSlice(1_000_000L, clock));

        assertEquals(0, withHandler.size());
        assertEquals(1, withoutHandler.size());
    }

    @Test
    @DisplayName("Concurrent slices from several threads are all counted in the last-minute window")
    void testConcurrentRecordingNotLost() throws InterruptedException {
        PluginMetrics metrics = new PluginMetrics("TestPlugin");
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 10_000; i++) {
                    metrics.recordSlice(1, 1, 0);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(80_000L, metrics.totalSteps());
        assertEquals(80_000L, metrics.nanosLastMinute());
    }

    @Test
    @DisplayName("Out-of-range config values fall back to defaults with a warning each")
    void testConfigValidation() {
        TickBudgetConfig d = TickBudgetConfig.defaults();
        TickBudgetConfig broken = new TickBudgetConfig(
                60_000_000L,   // margin larger than a tick
                -1L,           // negative floor
                d.defaultTaskBudgetNanos(),
                0L,            // zero cap
                -3,            // negative weight
                d.normalPriorityWeight(),
                d.highPriorityWeight(),
                d.criticalPriorityWeight(),
                true
        );
        List<String> warnings = new ArrayList<>();

        TickBudgetConfig fixed = broken.validated(warnings::add);

        assertEquals(d.withDebug(true), fixed);
        assertEquals(4, warnings.size());
    }

    @Test
    @DisplayName("Valid config passes validation unchanged and silently")
    void testValidConfigUnchanged() {
        List<String> warnings = new ArrayList<>();
        TickBudgetConfig config = TickBudgetConfig.defaults();

        assertEquals(config, config.validated(warnings::add));
        assertTrue(warnings.isEmpty());
    }
}
