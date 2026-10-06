package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.AsyncChunks;
import dev.denfry.tickbudget.api.BudgetedTask;
import dev.denfry.tickbudget.api.Target;
import dev.denfry.tickbudget.api.TaskBuilder;
import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import dev.denfry.tickbudget.core.task.TaskBuilderImpl;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class TickBudgetImpl implements TickBudget {

    private static final Logger LOGGER = Logger.getLogger("TickBudget");

    private final Plugin plugin;
    private final PlatformBridge bridge;
    private final BudgetRunner runner;
    private final MetricsTracker metricsTracker;
    private final TickBudgetConfig config;
    private final AsyncChunks asyncChunks;

    public TickBudgetImpl(
            Plugin plugin,
            PlatformBridge bridge,
            BudgetRunner runner,
            MetricsTracker metricsTracker,
            TickBudgetConfig config
    ) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.runner = runner;
        this.metricsTracker = metricsTracker;
        this.config = config;
        this.asyncChunks = (world, chunkX, chunkZ) -> bridge.loadChunkAsync(world, chunkX, chunkZ);
    }

    @Override
    public TaskBuilder run(Target target, BudgetedTask task) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        return new TaskBuilderImpl(
                plugin,
                target,
                task,
                metricsTracker.getOrCreate(plugin.getName()),
                runner::submit
        );
    }

    @Override
    public CompletableFuture<Void> schedule(Target target, Runnable action) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        return bridge.schedule(plugin, target, action);
    }

    @Override
    public void runOn(Target target, Runnable action) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action cannot be null");
        }
        if (isOwnedByCurrentThread(target)) {
            action.run();
        } else {
            schedule(target, action);
        }
    }

    @Override
    public boolean isOwnedByCurrentThread(Target target) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        return bridge.isOwnedByCurrentThread(target);
    }

    @Override
    public void assertOn(Target target) {
        if (!isOwnedByCurrentThread(target)) {
            String msg = String.format(
                    "Thread ownership violation: Current thread '%s' does not own target %s. " +
                    "Schedule execution with tb.run(target, ...) or tb.schedule(target, ...).",
                    Thread.currentThread().getName(), target
            );

            if (config.debugEnabled()) {
                LOGGER.log(Level.WARNING, "[TickBudget-Debug] " + msg + " Called by plugin: " + plugin.getName(),
                        new IllegalStateException(msg));
            }
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public AsyncChunks chunks() {
        return asyncChunks;
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        if (location == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        return bridge.teleportAsync(entity, location);
    }
}
