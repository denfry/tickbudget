package dev.denfry.tickbudget.api;

import org.bukkit.plugin.Plugin;

/** Registered in the Bukkit services manager by the TickBudget plugin. Use {@link TickBudget#of(Plugin)}. */
public interface TickBudgetService {

    TickBudget forPlugin(Plugin plugin);
}
