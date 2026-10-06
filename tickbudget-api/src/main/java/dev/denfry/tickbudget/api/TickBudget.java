package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Primary entry point for plugins integrating with TickBudget.
 * <p>
 * Obtain an instance via {@link TickBudget#of(Plugin)}.
 */
public interface TickBudget {

    /**
     * Acquires the {@link TickBudget} instance for the specified plugin.
     *
     * @param plugin the plugin acquiring the service
     * @return the TickBudget instance
     * @throws IllegalStateException if the TickBudget plugin is not installed or enabled
     */
    static TickBudget of(Plugin plugin) {
        RegisteredServiceProvider<TickBudgetService> registration =
                Bukkit.getServicesManager().getRegistration(TickBudgetService.class);
        if (registration == null) {
            throw new IllegalStateException(
                    "TickBudget is not enabled. Add 'depend: [TickBudget]' to " + plugin.getName() + "'s plugin.yml");
        }
        return registration.getProvider().forPlugin(plugin);
    }

    /**
     * Starts building a budgeted task that executes on the specified {@code target}.
     * Call {@link TaskBuilder#start()} on the returned builder to queue execution.
     *
     * @param target the execution domain (global, region, entity, or async)
     * @param task the stepped task logic
     * @return a builder to configure budget, priority, callbacks, and launch
     */
    TaskBuilder run(Target target, BudgetedTask task);

    /**
     * Runs {@code action} once on {@code target}, as soon as its scheduler permits.
     *
     * @param target the target execution context
     * @param action the task to run
     * @return a future completing when the action finishes
     */
    CompletableFuture<Void> schedule(Target target, Runnable action);

    /**
     * Runs {@code action} immediately if the current thread owns {@code target},
     * otherwise schedules it to run on the target's scheduler.
     *
     * @param target the target execution context
     * @param action the task to run
     */
    void runOn(Target target, Runnable action);

    /**
     * Checks whether the calling thread currently owns the execution context of {@code target}.
     *
     * @param target the target to test
     * @return true if owned by current thread
     */
    boolean isOwnedByCurrentThread(Target target);

    /**
     * Asserts that the current thread owns {@code target}.
     *
     * @param target the target context that must be owned
     * @throws IllegalStateException if the calling thread does not own {@code target}
     */
    void assertOn(Target target);

    /**
     * Returns the asynchronous chunk helper interface.
     *
     * @return chunk loading helper
     */
    AsyncChunks chunks();

    /**
     * Safely teleports an entity from any thread to the specified location.
     *
     * @param entity the entity to teleport
     * @param location the destination location
     * @return future completing with true if teleport succeeded, false if cancelled
     */
    CompletableFuture<Boolean> teleport(Entity entity, Location location);
}
