package dev.denfry.tickbudget.core.config;

import dev.denfry.tickbudget.api.Priority;

/**
 * Configuration parameters for TickBudget.
 */
public record TickBudgetConfig(
        long safetyMarginNanos,
        long floorBudgetNanos,
        long defaultTaskBudgetNanos,
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
                1,            // LOW weight
                2,            // NORMAL weight
                4,            // HIGH weight
                8,            // CRITICAL weight
                false         // debug mode disabled by default
        );
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
                lowPriorityWeight,
                normalPriorityWeight,
                highPriorityWeight,
                criticalPriorityWeight,
                debug
        );
    }
}
