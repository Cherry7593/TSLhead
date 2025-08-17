package com.zvbj.TSL_head;

import org.bukkit.plugin.java.JavaPlugin;

public class TSL_Head extends JavaPlugin {
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        // 注册头颅命令 - 修改为TSLhead
        HeadCommand headCommand = new HeadCommand(configManager, this);
        getCommand("tslhead").setExecutor(headCommand);
        getCommand("tslhead").setTabCompleter(headCommand);

        // 注册CrazyCrates联动命令 - 修改为tslheadcc
        CrazyCratesCommand cratesCommand = new CrazyCratesCommand(configManager, this);
        getCommand("tslheadcc").setExecutor(cratesCommand);
        getCommand("tslheadcc").setTabCompleter(cratesCommand);

        getServer().getPluginManager().registerEvents(new AnvilListener(), this);
        getLogger().info("TSLhead 已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("TSLhead 已禁用");
    }
}