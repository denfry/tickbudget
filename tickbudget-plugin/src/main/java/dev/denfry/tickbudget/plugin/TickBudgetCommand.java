package dev.denfry.tickbudget.plugin;

import dev.denfry.tickbudget.core.accounting.MetricsTracker;
import dev.denfry.tickbudget.core.accounting.PluginMetrics;
import dev.denfry.tickbudget.core.bridge.PlatformBridge;
import dev.denfry.tickbudget.core.config.TickBudgetConfig;
import dev.denfry.tickbudget.core.runner.BudgetRunner;
import dev.denfry.tickbudget.core.task.BudgetedTaskHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class TickBudgetCommand implements CommandExecutor, TabCompleter {

    private final TickBudgetPlugin plugin;
    private final PlatformBridge bridge;
    private final BudgetRunner runner;
    private final MetricsTracker metricsTracker;

    public TickBudgetCommand(
            TickBudgetPlugin plugin,
            PlatformBridge bridge,
            BudgetRunner runner,
            MetricsTracker metricsTracker
    ) {
        this.plugin = plugin;
        this.bridge = bridge;
        this.runner = runner;
        this.metricsTracker = metricsTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tickbudget.admin")) {
            sender.sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "status";

        switch (sub) {
            case "status" -> handleStatus(sender);
            case "top" -> handleTop(sender);
            case "debug" -> handleDebug(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Usage: /" + label + " [status|top|debug|reload]", NamedTextColor.RED));
            }
        }

        return true;
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(Component.text("---------------- [ TickBudget Status ] ----------------", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Platform: ", NamedTextColor.GRAY)
                .append(Component.text(bridge.name(), NamedTextColor.YELLOW))
                .append(Component.text(" | Active Tasks: ", NamedTextColor.GRAY))
                .append(Component.text(runner.activeTasks().size(), NamedTextColor.GREEN)));

        var metricsList = metricsTracker.allMetrics();
        if (metricsList.isEmpty()) {
            sender.sendMessage(Component.text("No registered plugins currently using TickBudget.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Plugin Metrics (last 60s):", NamedTextColor.AQUA));
            for (PluginMetrics pm : metricsList) {
                Component line = Component.text(" • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(pm.pluginName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(Component.text(String.format("%.2f ms", pm.millisLastMinute()), NamedTextColor.GOLD))
                        .append(Component.text(" | Tasks: ", NamedTextColor.GRAY))
                        .append(Component.text(pm.activeTasks(), NamedTextColor.GREEN))
                        .append(Component.text(" | Steps: ", NamedTextColor.GRAY))
                        .append(Component.text(pm.totalSteps(), NamedTextColor.YELLOW))
                        .append(Component.text(" | Deferred: ", NamedTextColor.GRAY))
                        .append(Component.text(pm.deferredTicks(), NamedTextColor.AQUA))
                        .append(Component.text(" | Violations: ", NamedTextColor.GRAY))
                        .append(Component.text(pm.violations(), pm.violations() > 0 ? NamedTextColor.RED : NamedTextColor.GREEN));
                sender.sendMessage(line);
            }
        }
        sender.sendMessage(Component.text("-------------------------------------------------------", NamedTextColor.GOLD));
    }

    private void handleTop(CommandSender sender) {
        sender.sendMessage(Component.text("----------------- [ TickBudget Top ] -----------------", NamedTextColor.GOLD, TextDecoration.BOLD));

        List<BudgetedTaskHandle> tasks = new ArrayList<>(runner.activeTasks());
        tasks.sort(Comparator.comparingDouble(BudgetedTaskHandle::totalMillis).reversed());

        if (tasks.isEmpty()) {
            sender.sendMessage(Component.text("No active budgeted tasks running right now.", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Top Tasks by Cumulative CPU Time:", NamedTextColor.AQUA));
            int limit = Math.min(tasks.size(), 10);
            for (int i = 0; i < limit; i++) {
                BudgetedTaskHandle t = tasks.get(i);
                Component line = Component.text(" #" + (i + 1) + " ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(t.name(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" (" + t.ownerPlugin().getName() + ")", NamedTextColor.GRAY))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format("%.2f ms total", t.totalMillis()), NamedTextColor.GOLD))
                        .append(Component.text(" | Steps: ", NamedTextColor.GRAY))
                        .append(Component.text(t.steps(), NamedTextColor.YELLOW))
                        .append(Component.text(" | Priority: ", NamedTextColor.GRAY))
                        .append(Component.text(t.priority().name(), NamedTextColor.AQUA));
                sender.sendMessage(line);
            }
        }
        sender.sendMessage(Component.text("-------------------------------------------------------", NamedTextColor.GOLD));
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean current = plugin.config().debugEnabled();
            sender.sendMessage(Component.text("TickBudget debug mode is currently ", NamedTextColor.GRAY)
                    .append(Component.text(current ? "ENABLED" : "DISABLED", current ? NamedTextColor.GREEN : NamedTextColor.RED)));
            sender.sendMessage(Component.text("Usage: /tickbudget debug <on|off>", NamedTextColor.YELLOW));
            return;
        }

        String arg = args[1].toLowerCase();
        boolean enable = arg.equals("on") || arg.equals("true") || arg.equals("1");
        plugin.setDebugEnabled(enable);

        sender.sendMessage(Component.text("TickBudget debug mode has been ", NamedTextColor.GRAY)
                .append(Component.text(enable ? "ENABLED" : "DISABLED", enable ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private void handleReload(CommandSender sender) {
        plugin.reload();
        sender.sendMessage(Component.text("TickBudget configuration reloaded successfully.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tickbudget.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("status", "top", "debug", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
