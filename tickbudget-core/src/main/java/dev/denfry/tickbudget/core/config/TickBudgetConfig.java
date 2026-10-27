package dev.denfry.tickbudget.core.config;

import dev.denfry.tickbudget.api.Priority;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Configuration parameters for TickBudget.
 */
public record TickBudgetConfig(
        long safetyMarginNanos,
        long floorBudgetNanos,
        long defaultTaskBudgetNanos,
        long maxTaskBudgetNanos,
        int lowPriorityWeight,
        int normalPriorityWeight,
        int highPriorityWeight,
        int criticalPriorityWeight,
        boolean debugEnabled
) {

    public static TickBudgetConfig defaults() {
        return new TickBudgetConfig(
                5_000_000L,   // 5 ms safety margin
                500_000L,     // 0.5 ms floor guarantee per plugin
                1_000_000L,   // 1 ms default per task
                20_000_000L,  // 20 ms hard cap per task
                1,            // LOW weight
                2,            // NORMAL weight
                4,            // HIGH weight
                8,            // CRITICAL weight
                false         // debug mode disabled by default
        );
    }

    private static final long TICK_NANOS = 50_000_000L;

    /**
     * Returns a copy in which every out-of-range value is replaced by its default, reporting each replacement.
     */
    public TickBudgetConfig validated(Consumer<String> warnings) {
        TickBudgetConfig d = defaults();
        return new TickBudgetConfig(
                checkNanos("safety-margin-ms", safetyMarginNanos, 0L, TICK_NANOS - 1, d.safetyMarginNanos, warnings),
                checkNanos("floor-budget-ms", floorBudgetNanos, 0L, TICK_NANOS, d.floorBudgetNanos, warnings),
                checkNanos("default-task-budget-ms", defaultTaskBudgetNanos, 1L, TICK_NANOS, d.defaultTaskBudgetNanos, warnings),
                checkNanos("max-task-budget-ms", maxTaskBudgetNanos, 1L, TICK_NANOS, d.maxTaskBudgetNanos, warnings),
                checkWeight("priority-weights.low", lowPriorityWeight, d.lowPriorityWeight, warnings),
                checkWeight("priority-weights.normal", normalPriorityWeight, d.normalPriorityWeight, warnings),
                checkWeight("priority-weights.high", highPriorityWeight, d.highPriorityWeight, warnings),
                checkWeight("priority-weights.critical", criticalPriorityWeight, d.criticalPriorityWeight, warnings),
                debugEnabled
        );
    }

    private static long checkNanos(String key, long value, long min, long max, long fallback, Consumer<String> warnings) {
        if (value >= min && value <= max) {
            return value;
        }
        warnings.accept(String.format(Locale.ROOT, "%s must be between %.3f and %.3f ms; using default %.3f ms",
                key, min / 1_000_000.0, max / 1_000_000.0, fallback / 1_000_000.0));
        return fallback;
    }

    private static int checkWeight(String key, int value, int fallback, Consumer<String> warnings) {
        if (value >= 0) {
            return value;
        }
        warnings.accept(key + " must not be negative; using default " + fallback);
        return fallback;
    }

    public int weightFor(Priority priority) {
        return switch (priority) {
            case LOW -> lowPriorityWeight;
            case NORMAL -> normalPriorityWeight;
            case HIGH -> highPriorityWeight;
            case CRITICAL -> criticalPriorityWeight;
        };
    }

    public TickBudgetConfig withDebug(boolean debug) {
        return new TickBudgetConfig(
                safetyMarginNanos,
                floorBudgetNanos,
                defaultTaskBudgetNanos,
                maxTaskBudgetNanos,
                lowPriorityWeight,
                normalPriorityWeight,
                highPriorityWeight,
                criticalPriorityWeight,
                debug
        );
    }
}
