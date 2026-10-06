package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class FakePlatformBridge implements PlatformBridge {

    private boolean folia = false;
    private boolean owned = true;
    private long tickElapsedNanos = -1L;

    public void setFolia(boolean folia) {
        this.folia = folia;
    }

    public void setOwned(boolean owned) {
        this.owned = owned;
    }

    public void setTickElapsedNanos(long tickElapsedNanos) {
        this.tickElapsedNanos = tickElapsedNanos;
    }

    @Override
    public long currentTickElapsedNanos() {
        return tickElapsedNanos;
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
    public TaskCancellable scheduleRepeating(Plugin plugin, Target target, Runnable action, long periodTicks) {
        return () -> {};
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
