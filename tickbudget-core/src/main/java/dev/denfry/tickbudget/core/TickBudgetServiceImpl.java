package dev.denfry.tickbudget.core;

import dev.denfry.tickbudget.api.TickBudget;
import dev.denfry.tickbudget.api.TickBudgetService;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;

public final class TickBudgetServiceImpl implements TickBudgetService {

    private final PlatformBridge bridge;
    private final BudgetRunner runner;
    private final MetricsTracker metricsTracker;
    private final Supplier<TickBudgetConfig> config;

    private final ConcurrentMap<Plugin, TickBudget> instances = new ConcurrentHashMap<>();

    public TickBudgetServiceImpl(
            PlatformBridge bridge,
            BudgetRunner runner,
            MetricsTracker metricsTracker,
            Supplier<TickBudgetConfig> config
    ) {
        this.bridge = bridge;
        this.runner = runner;
        this.metricsTracker = metricsTracker;
        this.config = config;
    }

    @Override
    public TickBudget forPlugin(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }
        // Shares are granted per plugin, so only real, enabled plugins may hold an instance
        if (!plugin.isEnabled() || !bridge.isRegisteredPlugin(plugin)) {
            throw new IllegalArgumentException(
                    "Plugin '" + plugin.getName() + "' is not an enabled plugin registered with the server");
        }
        return instances.computeIfAbsent(plugin, p ->
                new TickBudgetImpl(p, bridge, runner, metricsTracker, config));
    }

    public void onPluginDisabled(Plugin plugin) {
        instances.remove(plugin);
        runner.cancelTasksForPlugin(plugin);
        metricsTracker.remove(plugin.getName());
    }
}
