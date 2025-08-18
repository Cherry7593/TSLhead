package com.zvbj.TSL_head;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeadCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final TSL_Head plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public HeadCommand(ConfigManager configManager, TSL_Head plugin) {
        this.configManager = configManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tslhead.use")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            configManager.loadConfigs();
            sender.sendMessage(Component.text("TSLhead 配置已重新加载", NamedTextColor.GREEN));
            return true;
        }

        // 支持2个参数（默认给自己）或3个参数的情况
        if (args.length == 2 || args.length == 3) {
            String key = args[0];
            SkullConfig sc = configManager.getConfig(key);
            if (sc == null) {
                sender.sendMessage(Component.text("未找到命名: " + key, NamedTextColor.RED));
                return true;
            }

            // 如果只有2个参数，接收者默认为命令发送者
            final Player receiver;
            if (args.length == 2) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("控制台必须指定接收玩家", NamedTextColor.RED));
                    return true;
                }
                receiver = (Player) sender;
            } else {
                // 3个参数的情况，使用指定的接收者
                receiver = Bukkit.getPlayerExact(args[2]);
                if (receiver == null) {
                    sender.sendMessage(Component.text("接收玩家 " + args[2] + " 不在线", NamedTextColor.RED));
                    return true;
                }
            }

            final String targetPlayerName = args[1];

            // Folia专用：使用异步任务处理头颅获取
            CompletableFuture.supplyAsync(() -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);

                // 移除原来的限制，允许获取正版离线玩家头颅
                // 只有在明确知道是无效用户名时才拒绝（这里我们信任用户输入正确的正版用户名）

                return createSkull(target, sc, targetPlayerName);
            }).thenAccept(skull -> {
                if (skull == null) {
                    receiver.getScheduler().run(plugin, (scheduledTask) -> {
                        sender.sendMessage(Component.text("创建头颅失败", NamedTextColor.RED));
                    }, null);
                    return;
                }

                // 切换回主线程执行物品操作
                receiver.getScheduler().run(plugin, (scheduledTask) -> {
                    giveSkullToPlayer(sender, receiver, skull, targetPlayerName, key);
                }, null);
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("异步处理头颅时发生错误: " + throwable.getMessage());
                return null;
            });

            return true;
        }

        sender.sendMessage(Component.text("用法: /tslhead reload OR /tslhead <命名> <玩家名> [接收玩家]", NamedTextColor.YELLOW));
        return true;
    }

    private void giveSkullToPlayer(CommandSender sender, Player receiver, ItemStack skull, String targetPlayerName, String key) {
        // 尝试将头颅添加到玩家背包，如果背包满了则掉落到地上
        HashMap<Integer, ItemStack> leftover = receiver.getInventory().addItem(skull);

        // 检查是否有剩余物品（背包满了）
        if (!leftover.isEmpty()) {
            // 背包满了，将头颅掉落到玩家脚下
            for (ItemStack item : leftover.values()) {
                receiver.getWorld().dropItemNaturally(receiver.getLocation(), item);
            }
            sender.sendMessage(Component.text("玩家 " + receiver.getName() + " 的背包已满，头颅已掉落到其脚下", NamedTextColor.YELLOW));
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);
        String targetStatus = target.isOnline() ? "在线" : "离线";
        sender.sendMessage(Component.text("已将 " + targetPlayerName + " (" + targetStatus + ") 的头颅 (" + key + ") 给予 " + receiver.getName(), NamedTextColor.GREEN));
    }

    private @Nullable ItemStack createSkull(OfflinePlayer target, SkullConfig sc, String fallbackName) {
        try {
            ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                // 统一使用输入的玩家名，不区分在线离线状态
                // 这样可以确保一致性，类似CMI插件的做法
                String playerName = fallbackName;

                // 统一的头颅设置策略：始终使用玩家名
                // 这样Minecraft客户端会自动查询Mojang API获取正确的皮肤
                meta.setOwner(fallbackName);
                plugin.getLogger().info("创建头颅: " + fallbackName + " (统一使用玩家名查询皮肤)");

                Component name = formatToComponent(sc.getNameTemplate(), playerName);
                meta.displayName(name);

                List<Component> lore = sc.getLoreTemplate().stream()
                        .map(line -> formatToComponent(line, playerName))
                        .toList();
                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            return skull;
        } catch (Exception e) {
            plugin.getLogger().warning("创建头颅时发生错误: " + e.getMessage());
            e.printStackTrace(); // 添加详细错误信息
            return null;
        }
    }

    private Component formatToComponent(String template, String playerName) {
        String replaced = template.replace("{Player_name}", playerName);

        // 修复后的16进制颜色处理
        String processed = processHexColors(replaced);

        try {
            // 优先使用MiniMessage解析（支持现代颜色格式）
            Component component = miniMessage.deserialize(processed);
            // 强制禁用斜体装饰（针对lore��特殊处理）
            return component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        } catch (Exception e) {
            // 如果MiniMessage解析失败，尝试使用传统方法
            plugin.getLogger().warning("MiniMessage解析失败，尝试传统方法: " + e.getMessage());
            try {
                // 创建无斜体的组件
                return Component.text(replaced).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            } catch (Exception ex) {
                // 最后的fallback
                plugin.getLogger().warning("颜色处理完全失败，使用纯文本: " + ex.getMessage());
                return Component.text(playerName).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
            }
        }
    }

    private String processHexColors(String input) {
        // 将 &#RRGGBB 转换为 <#RRGGBB> (MiniMessage格式)
        String result = input.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");

        // 处理传统颜色代码 &x -> <color>
        result = result.replace("&0", "<black>");
        result = result.replace("&1", "<dark_blue>");
        result = result.replace("&2", "<dark_green>");
        result = result.replace("&3", "<dark_aqua>");
        result = result.replace("&4", "<dark_red>");
        result = result.replace("&5", "<dark_purple>");
        result = result.replace("&6", "<gold>");
        result = result.replace("&7", "<gray>");
        result = result.replace("&8", "<dark_gray>");
        result = result.replace("&9", "<blue>");
        result = result.replace("&a", "<green>");
        result = result.replace("&b", "<aqua>");
        result = result.replace("&c", "<red>");
        result = result.replace("&d", "<light_purple>");
        result = result.replace("&e", "<yellow>");
        result = result.replace("&f", "<white>");

        // 处理格式代码
        result = result.replace("&l", "<bold>");
        result = result.replace("&m", "<strikethrough>");
        result = result.replace("&n", "<underlined>");
        result = result.replace("&o", "<italic>");
        result = result.replace("&k", "<obfuscated>");
        result = result.replace("&r", "<reset>");

        // 移除之前错误的italic:false标签
        return result;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(List.of("reload"));
            completions.addAll(configManager.getKeys().stream()
                    .filter(k -> k.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList());
            return completions;
        }

        if (args.length == 2) {
            // 只补全在线玩家，避免性能问题
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3) {
            // 接收玩家只能是在线玩家
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
