package com.zvbj.TSL_head;

import org.bukkit.plugin.java.JavaPlugin;

public class TSL_Head extends JavaPlugin {
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(this);

        // 注册头颅命令
        HeadCommand headCommand = new HeadCommand(configManager, this);
        getCommand("tslhead").setExecutor(headCommand);
        getCommand("tslhead").setTabCompleter(headCommand);

        // 注册CrazyCrates联动命令
        CrazyCratesCommand cratesCommand = new CrazyCratesCommand(configManager, this);
        getCommand("tslheadcc").setExecutor(cratesCommand);
        getCommand("tslheadcc").setTabCompleter(cratesCommand);

        // 注册铁砧保护监听器
        getServer().getPluginManager().registerEvents(new AnvilListener(), this);

        getLogger().info("TSLhead Folia专版已启用 - 支持16进制颜色和区域调度器");
    }

    @Override
    public void onDisable() {
        getLogger().info("TSLhead Folia专版已禁用");
    }
}