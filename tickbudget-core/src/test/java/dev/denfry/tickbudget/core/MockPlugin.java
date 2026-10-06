package dev.denfry.tickbudget.core;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginBase;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;

public class MockPlugin extends PluginBase {

    private final String name;
    private final PluginDescriptionFile description;

    public MockPlugin(String name) {
        this.name = name;
        this.description = new PluginDescriptionFile(name, "1.0.0", "dev.denfry.MockPlugin");
    }

    public String namespace() {
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public PluginDescriptionFile getDescription() {
        return description;
    }

    @Override
    public Logger getLogger() {
        return Logger.getLogger(name);
    }

    @Override public File getDataFolder() { return null; }
    @Override public FileConfiguration getConfig() { return null; }
    @Override public InputStream getResource(String filename) { return null; }
    @Override public void saveConfig() {}
    @Override public void saveDefaultConfig() {}
    @Override public void saveResource(String resourcePath, boolean replace) {}
    @Override public void reloadConfig() {}
    @SuppressWarnings("removal")
    @Override public PluginLoader getPluginLoader() { return null; }
    @Override public io.papermc.paper.plugin.configuration.PluginMeta getPluginMeta() { return description; }
    @Override public io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager<Plugin> getLifecycleManager() { return null; }
    @Override public Server getServer() { return null; }
    @Override public boolean isEnabled() { return true; }
    @Override public void onDisable() {}
    @Override public void onLoad() {}
    @Override public void onEnable() {}
    @Override public boolean isNaggable() { return false; }
    @Override public void setNaggable(boolean canNag) {}
    @Override public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) { return null; }
    @Override public BiomeProvider getDefaultBiomeProvider(String worldName, String id) { return null; }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return List.of(); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
}
