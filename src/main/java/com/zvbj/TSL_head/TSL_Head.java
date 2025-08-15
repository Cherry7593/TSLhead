package com.zvbj.TSL_head;

import org.bukkit.plugin.java.JavaPlugin;

public class TSL_Head extends JavaPlugin {
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        HeadCommand command = new HeadCommand(configManager);
        getCommand("TSLhead").setExecutor(command);
        getCommand("TSLhead").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new AnvilListener(), this);
        getLogger().info("TSLhead 已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("TSLhead 已禁用");
    }
}