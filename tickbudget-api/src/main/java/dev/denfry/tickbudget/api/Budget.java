package dev.denfry.tickbudget.api;

/**
 * Maximum time one task may spend per tick. Enforced between steps, so a single long step can overshoot it.
 *
 * @param nanosPerTick maximum nanoseconds allowed per tick
 */
public record Budget(long nanosPerTick) {
    /**
     * Validates that the budget nanoseconds value is strictly positive.
     */
    public Budget {
        if (nanosPerTick <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
    }

    /**
     * Creates a per-tick budget from milliseconds.
     *
     * @param millis allowed milliseconds per tick
     * @return the budget instance
     */
    public static Budget millisPerTick(double millis) {
        return new Budget(Math.max(1L, Math.round(millis * 1_000_000d)));
    }
}
