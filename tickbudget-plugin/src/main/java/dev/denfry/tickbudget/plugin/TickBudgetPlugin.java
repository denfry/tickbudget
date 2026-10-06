package dev.denfry.tickbudget.plugin;

import dev.denfry.tickbudget.api.TickBudgetService;
import dev.denfry.tickbudget.core.SystemClock;
import dev.denfry.tickbudget.core.TickBudgetServiceImpl;
import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import dev.denfry.tickbudget.folia.FoliaPlatformBridge;
import dev.denfry.tickbudget.paper.PaperPlatformBridge;
import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class TickBudgetPlugin extends JavaPlugin implements Listener {

    private PlatformBridge bridge;
    private MetricsTracker metricsTracker;
    private BudgetRunner runner;
    private TickBudgetServiceImpl service;
    private TickBudgetConfig config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = loadConfig();

        this.bridge = detectBridge();
        this.bridge.init(this);
        getLogger().info("Initializing TickBudget with platform bridge: " + bridge.name());

        this.metricsTracker = new MetricsTracker();
        this.runner = new BudgetRunner(bridge, config, metricsTracker, SystemClock.DEFAULT);
        this.service = new TickBudgetServiceImpl(bridge, runner, metricsTracker, config);

        // Register the public TickBudgetService in Bukkit services manager
        Bukkit.getServicesManager().register(
                TickBudgetService.class,
                service,
                this,
                ServicePriority.Highest
        );

        // Register plugin disable listener for cleanup
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register administration command
        var cmd = getCommand("tickbudget");
        if (cmd != null) {
            TickBudgetCommand executor = new TickBudgetCommand(this, bridge, runner, metricsTracker);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // Start runner dispatcher
        runner.start(this);

        // Initialize bStats metrics (relocated to dev.denfry.tickbudget.plugin.metrics.bstats in jar)
        if (getConfig().getBoolean("metrics", true)) {
            int pluginId = getConfig().getInt("bstats-id", 24680);
            try {
                Metrics bMetrics = new Metrics(this, pluginId);
                bMetrics.addCustomChart(new SimplePie("platform", () -> bridge.name()));
                bMetrics.addCustomChart(new SingleLineChart("active_client_plugins", () -> metricsTracker.allMetrics().size()));
            } catch (Throwable t) {
                getLogger().fine("Could not initialize bStats metrics: " + t.getMessage());
            }
        }

        getLogger().info("TickBudget v" + getPluginMeta().getVersion() + " successfully enabled on " + bridge.name() + "!");
    }

    @Override
    public void onDisable() {
        if (runner != null) {
            runner.stop();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        getLogger().info("TickBudget disabled.");
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() != this && service != null) {
            service.onPluginDisabled(event.getPlugin());
        }
    }

    private PlatformBridge detectBridge() {
        boolean isFolia = false;
        try {
            isFolia = ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"));
        } catch (Throwable ignored) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }

        if (isFolia) {
            return new FoliaPlatformBridge();
        } else {
            return new PaperPlatformBridge();
        }
    }

    private TickBudgetConfig loadConfig() {
        FileConfiguration cfg = getConfig();
        long safetyMarginNanos = (long) (cfg.getDouble("safety-margin-ms", 5.0) * 1_000_000L);
        long floorBudgetNanos = (long) (cfg.getDouble("floor-budget-ms", 0.5) * 1_000_000L);
        long defaultBudgetNanos = (long) (cfg.getDouble("default-task-budget-ms", 1.0) * 1_000_000L);
        int lowWeight = cfg.getInt("priority-weights.low", 1);
        int normalWeight = cfg.getInt("priority-weights.normal", 2);
        int highWeight = cfg.getInt("priority-weights.high", 4);
        int criticalWeight = cfg.getInt("priority-weights.critical", 8);
        boolean debug = cfg.getBoolean("debug", false);

        return new TickBudgetConfig(
                safetyMarginNanos,
                floorBudgetNanos,
                defaultBudgetNanos,
                lowWeight,
                normalWeight,
                highWeight,
                criticalWeight,
                debug
        );
    }

    public TickBudgetConfig config() {
        return config;
    }

    public void setDebugEnabled(boolean debug) {
        this.config = config.withDebug(debug);
        getConfig().set("debug", debug);
        saveConfig();
    }

    public void reload() {
        reloadConfig();
        this.config = loadConfig();
    }
}
