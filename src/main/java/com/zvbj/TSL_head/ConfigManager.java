package com.zvbj.TSL_head;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ConfigManager {
    private final JavaPlugin plugin;
    private final Map<String, SkullConfig> headConfigs = new HashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {
        plugin.reloadConfig();
        headConfigs.clear();
        FileConfiguration cfg = plugin.getConfig();
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection section = cfg.getConfigurationSection(key);
            if (section != null) {
                String name = section.getString("name");
                List<String> lore = section.getStringList("lore");
                headConfigs.put(key.toLowerCase(), new SkullConfig(name, lore));
            }
        }
    }

    public SkullConfig getConfig(String key) {
        return headConfigs.get(key.toLowerCase());
    }

    public Set<String> getKeys() {
        return headConfigs.keySet();
    }
}