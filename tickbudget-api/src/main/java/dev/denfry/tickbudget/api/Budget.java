package dev.denfry.tickbudget.api;

/** Maximum time one task may spend per tick. Enforced between steps, so a single long step can overshoot it. */
public record Budget(long nanosPerTick) {
    public Budget {
        if (nanosPerTick <= 0) {
            throw new IllegalArgumentException("budget must be positive");
        }
    }

    public static Budget millisPerTick(double millis) {
        return new Budget(Math.max(1L, Math.round(millis * 1_000_000d)));
    }
}
