package dev.denfry.tickbudget.core.accounting;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry and coordinator of metrics across all plugins using TickBudget.
 */
public final class MetricsTracker {

    private final ConcurrentMap<String, PluginMetrics> metricsByPlugin = new ConcurrentHashMap<>();

    public PluginMetrics getOrCreate(String pluginName) {
        return metricsByPlugin.computeIfAbsent(pluginName, PluginMetrics::new);
    }

    public PluginMetrics get(String pluginName) {
        return metricsByPlugin.get(pluginName);
    }

    public Collection<PluginMetrics> allMetrics() {
        return Collections.unmodifiableCollection(metricsByPlugin.values());
    }

    public void remove(String pluginName) {
        metricsByPlugin.remove(pluginName);
    }

    public void clear() {
        metricsByPlugin.clear();
    }
}
