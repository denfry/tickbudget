package dev.denfry.tickbudget.api;

import org.bukkit.plugin.Plugin;

/**
 * Service registered in the Bukkit services manager by the TickBudget plugin.
 * <p>
 * Standard consumer plugins should use {@link TickBudget#of(Plugin)} rather than accessing this service directly.
 */
public interface TickBudgetService {

    /**
     * Obtains the {@link TickBudget} instance for the specified plugin.
     *
     * @param plugin the owning plugin
     * @return the plugin-scoped TickBudget instance
     */
    TickBudget forPlugin(Plugin plugin);
}
