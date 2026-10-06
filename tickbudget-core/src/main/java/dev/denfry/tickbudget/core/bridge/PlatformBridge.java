package dev.denfry.tickbudget.core.bridge;

import dev.denfry.tickbudget.api.Target;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Abstraction over server platforms (Paper, Folia, or test mock).
 * Core has zero direct dependencies on Folia or Paper-specific internals.
 */
public interface PlatformBridge {

    String name();

    boolean isFolia();

    default void init(Plugin plugin) {
    }

    /**
     * Returns nanoseconds elapsed so far in the current server tick,
     * or -1 if the platform does not provide tick-start tracking.
     */
    default long currentTickElapsedNanos() {
        return -1L;
    }

    boolean isOwnedByCurrentThread(Target target);

    /**
     * Executes {@code action} once on {@code target}'s owning thread/scheduler.
     */
    CompletableFuture<Void> schedule(Plugin plugin, Target target, Runnable action);

    /**
     * Schedules a repeating tick action on {@code target}.
     * Returns a handle to cancel the repeating task.
     */
    TaskCancellable scheduleRepeating(Plugin plugin, Target target, Runnable action, long periodTicks);

    CompletableFuture<Chunk> loadChunkAsync(World world, int chunkX, int chunkZ);

    CompletableFuture<Boolean> teleportAsync(Entity entity, Location location);

    @FunctionalInterface
    interface TaskCancellable {
        void cancel();
    }
}
