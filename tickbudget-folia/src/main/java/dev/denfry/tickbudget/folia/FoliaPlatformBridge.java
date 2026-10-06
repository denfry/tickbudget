package dev.denfry.tickbudget.folia;

import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Platform bridge for Folia servers (regionized multithreading).
 */
public final class FoliaPlatformBridge implements PlatformBridge {

    @Override
    public String name() {
        return "Folia";
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentThread(Target target) {
        if (target instanceof Target.OfAsync) {
            return !Bukkit.isPrimaryThread();
        } else if (target instanceof Target.OfGlobal) {
            return Bukkit.getServer().isGlobalTickThread();
        } else if (target instanceof Target.OfRegion r) {
            return Bukkit.isOwnedByCurrentRegion(r.world(), r.chunkX(), r.chunkZ());
        } else if (target instanceof Target.OfEntity e) {
            return Bukkit.isOwnedByCurrentRegion(e.entity());
        }
        return false;
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
            Bukkit.getAsyncScheduler().runNow(plugin, task -> wrapped.run());
        } else if (target instanceof Target.OfGlobal) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, wrapped);
        } else if (target instanceof Target.OfRegion r) {
            Bukkit.getRegionScheduler().execute(plugin, r.world(), r.chunkX(), r.chunkZ(), wrapped);
        } else if (target instanceof Target.OfEntity e) {
            e.entity().getScheduler().execute(plugin, wrapped, null, 0L);
        } else {
            wrapped.run();
        }

        return future;
    }

    @Override
    public TaskCancellable scheduleRepeating(Plugin plugin, Target target, Runnable action, long periodTicks) {
        ScheduledTask task;
        if (target instanceof Target.OfAsync) {
            long millis = Math.max(1L, periodTicks * 50L);
            task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> action.run(), 0L, millis, TimeUnit.MILLISECONDS);
        } else if (target instanceof Target.OfGlobal) {
            task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> action.run(), 1L, periodTicks);
        } else if (target instanceof Target.OfRegion r) {
            task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, r.world(), r.chunkX(), r.chunkZ(), t -> action.run(), 1L, periodTicks);
        } else if (target instanceof Target.OfEntity e) {
            task = e.entity().getScheduler().runAtFixedRate(plugin, t -> action.run(), null, 1L, periodTicks);
        } else {
            task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> action.run(), 1L, periodTicks);
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
