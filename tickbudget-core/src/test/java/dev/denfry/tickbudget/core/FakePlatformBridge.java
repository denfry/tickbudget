package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class FakePlatformBridge implements PlatformBridge {

    private boolean folia = false;
    private boolean owned = true;
    private long lastTickDurationNanos = -1L;
    private boolean runRepeatingOnSchedule = false;
    private boolean pluginsRegistered = true;
    private final List<Repeating> repeating = new ArrayList<>();

    /** A repeating task registered through {@link #scheduleRepeating}. */
    public static final class Repeating {
        public final Runnable action;
        public final Runnable onRetired;
        public boolean cancelled;

        Repeating(Runnable action, Runnable onRetired) {
            this.action = action;
            this.onRetired = onRetired;
        }
    }

    /** Runs each repeating action once inside {@code scheduleRepeating}, like a scheduler with zero initial delay. */
    public void setRunRepeatingOnSchedule(boolean runRepeatingOnSchedule) {
        this.runRepeatingOnSchedule = runRepeatingOnSchedule;
    }

    public List<Repeating> repeating() {
        return repeating;
    }

    public void setPluginsRegistered(boolean pluginsRegistered) {
        this.pluginsRegistered = pluginsRegistered;
    }

    @Override
    public boolean isRegisteredPlugin(Plugin plugin) {
        return pluginsRegistered;
    }

    public void setFolia(boolean folia) {
        this.folia = folia;
    }

    public void setOwned(boolean owned) {
        this.owned = owned;
    }

    public void setLastTickDurationNanos(long lastTickDurationNanos) {
        this.lastTickDurationNanos = lastTickDurationNanos;
    }

    @Override
    public long lastTickDurationNanos() {
        return lastTickDurationNanos;
    }

    @Override
    public String name() {
        return folia ? "FakeFolia" : "FakePaper";
    }

    @Override
    public boolean isFolia() {
        return folia;
    }

    @Override
    public boolean isOwnedByCurrentThread(Target target) {
        return owned;
    }

    @Override
    public CompletableFuture<Void> schedule(Plugin plugin, Target target, Runnable action) {
        action.run();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public TaskCancellable scheduleRepeating(Plugin plugin, Target target, Runnable action, Runnable onRetired, long periodTicks) {
        Repeating entry = new Repeating(action, onRetired);
        repeating.add(entry);
        if (runRepeatingOnSchedule) {
            action.run();
        }
        return () -> entry.cancelled = true;
    }

    @Override
    public CompletableFuture<Chunk> loadChunkAsync(World world, int chunkX, int chunkZ) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Entity entity, Location location) {
        return CompletableFuture.completedFuture(true);
    }
}
