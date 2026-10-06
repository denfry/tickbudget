package dev.denfry.tickbudget.paper;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Platform bridge for standard Paper servers (single main thread).
 */
public final class PaperPlatformBridge implements PlatformBridge, Listener {

    private volatile long currentTickStartNanos = -1L;

    @Override
    public String name() {
        return "Paper";
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public void init(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onTickStart(ServerTickStartEvent event) {
        this.currentTickStartNanos = System.nanoTime();
    }

    @Override
    public long currentTickElapsedNanos() {
        long start = currentTickStartNanos;
        if (start <= 0L) {
            return -1L;
        }
        return Math.max(0L, System.nanoTime() - start);
    }

    @Override
    public boolean isOwnedByCurrentThread(Target target) {
        if (target instanceof Target.OfAsync) {
            return !Bukkit.isPrimaryThread();
        }
        return Bukkit.isPrimaryThread();
    }

    @Override
    public CompletableFuture<Void> schedule(Plugin plugin, Target target, Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (target instanceof Target.OfAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, wrapped);
        } else {
            Bukkit.getScheduler().runTask(plugin, wrapped);
        }
        return future;
    }

    @Override
    public TaskCancellable scheduleRepeating(Plugin plugin, Target target, Runnable action, long periodTicks) {
        BukkitTask task;
        if (target instanceof Target.OfAsync) {
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, action, 0L, periodTicks);
        } else {
            task = Bukkit.getScheduler().runTaskTimer(plugin, action, 0L, periodTicks);
        }
        return task::cancel;
    }

    @Override
    public CompletableFuture<Chunk> loadChunkAsync(World world, int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Entity entity, Location location) {
        return entity.teleportAsync(location);
    }
}
