package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Entry point for a plugin that depends on TickBudget ({@code depend: [TickBudget]}). */
public interface TickBudget {

    static TickBudget of(Plugin plugin) {
        RegisteredServiceProvider<TickBudgetService> registration =
                Bukkit.getServicesManager().getRegistration(TickBudgetService.class);
        if (registration == null) {
            throw new IllegalStateException(
                    "TickBudget is not enabled. Add 'depend: [TickBudget]' to " + plugin.getName() + "'s plugin.yml");
        }
        return registration.getProvider().forPlugin(plugin);
    }

    /** Starts building a budgeted task that runs on {@code target}. Call {@code start()} to queue it. */
    TaskBuilder run(Target target, BudgetedTask task);

    /** Runs {@code action} once on {@code target}, as soon as its scheduler allows. */
    CompletableFuture<Void> schedule(Target target, Runnable action);

    /** Runs {@code action} right now if the current thread owns {@code target}, otherwise schedules it there. */
    void runOn(Target target, Runnable action);

    boolean isOwnedByCurrentThread(Target target);

    /**
     * Throws {@link IllegalStateException} with a hint on the right scheduler when the current thread does not own
     * {@code target}. In debug mode the offending plugin frame is logged as well.
     */
    void assertOn(Target target);

    AsyncChunks chunks();

    /** Teleports from any thread; completes with {@code false} if the teleport was cancelled. */
    CompletableFuture<Boolean> teleport(Entity entity, Location location);
}
