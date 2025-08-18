package com.zvbj.TSL_head;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

public class CrazyCratesCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final TSL_Head plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CrazyCratesCommand(@NotNull ConfigManager configManager, @NotNull TSL_Head plugin) {
        this.configManager = configManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("tslhead.crazycrates")) {
            sender.sendMessage(Component.text("你没有权限执行此命令", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("此命令只能由玩家执行", NamedTextColor.RED));
            return true;
        }

        final Player player = (Player) sender;

        if (args.length < 4) {
            sender.sendMessage(Component.text("用法: /tslheadcc <头颅类型> <玩家名> <奖池名> <权重> [层级]", NamedTextColor.YELLOW));
            return true;
        }

        final String skullType = args[0];
        final String playerName = args[1];
        final String crateName = args[2];
        final double weight;
        final String tier = args.length > 4 ? args[4] : null;

        // 验证权重参数
        try {
            weight = Double.parseDouble(args[3]);
            if (weight <= 0) {
                sender.sendMessage(Component.text("权重必须大于0", NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("权重必须是有效的数字", NamedTextColor.RED));
            return true;
        }

        // 检查头颅类型配置
        SkullConfig sc = configManager.getConfig(skullType);
        if (sc == null) {
            sender.sendMessage(Component.text("未找到头颅类型: " + skullType, NamedTextColor.RED));
            return true;
        }

        // 检查CrazyCrates插件是否存在
        if (!Bukkit.getPluginManager().isPluginEnabled("CrazyCrates")) {
            sender.sendMessage(Component.text("CrazyCrates插件未安装或未启用", NamedTextColor.RED));
            return true;
        }

        // Folia专用：使用CompletableFuture异步处理头颅创建和添加
        CompletableFuture.supplyAsync(() -> {
            // 检查目标玩家
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

            // 移除原来的限制，允许获取正版离线玩家头颅
            // 只有在明确知道是无效用户名时才拒绝（这里我们信任用户输入正确的正版用户名）

            // 创建头颅物品
            return createSkull(target, sc);
        }).thenAccept(skull -> {
            if (skull == null) {
                // 切换回主线程发送错误消息
                player.getScheduler().run(plugin, (scheduledTask) -> {
                    sender.sendMessage(Component.text("创建头颅失败", NamedTextColor.RED));
                }, null);
                return;
            }

            // 切换到主线程执行CrazyCrates命令
            player.getScheduler().run(plugin, (scheduledTask) -> {
                executeAddToCrate(sender, player, skull, skullType, playerName, crateName, weight, tier);
            }, null);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("异步处理CrazyCrates命令时发生错误: " + throwable.getMessage());
            return null;
        });

        return true;
    }

    private void executeAddToCrate(CommandSender sender, Player player, ItemStack skull, String skullType,
                                   String playerName, String crateName, double weight, String tier) {
        // 保存玩家当前手持物品
        ItemStack originalItem = player.getInventory().getItemInMainHand().clone();

        try {
            // 临时将头颅放入玩家手中
            player.getInventory().setItemInMainHand(skull);

            // 构建CrazyCrates命令
            String crateCommand;
            if (tier != null && !tier.isEmpty()) {
                crateCommand = String.format("crazycrates additem %s %s_%s %s %s",
                    crateName, skullType, playerName, weight, tier);
            } else {
                crateCommand = String.format("crazycrates additem %s %s_%s %s",
                    crateName, skullType, playerName, weight);
            }

            // 执行CrazyCrates命令
            boolean success = Bukkit.dispatchCommand(player, crateCommand);

            if (success) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                String targetStatus = target.isOnline() ? "在线" : "离线";
                String tierInfo = tier != null ? " [层级: " + tier + "]" : "";
                sender.sendMessage(Component.text("成功将 " + playerName + " (" + targetStatus +
                    ") 的 " + skullType + " 头颅添加到奖池 " + crateName + " (权重: " + weight + ")" + tierInfo, NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("添加头颅到奖池失败，请检查奖池名称是否正确", NamedTextColor.RED));
            }

        } finally {
            // 恢复玩家原来的手持物品
            player.getInventory().setItemInMainHand(originalItem);
        }
    }

    private @Nullable ItemStack createSkull(@NotNull OfflinePlayer target, @NotNull SkullConfig sc) {
        try {
            ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();

            if (meta != null) {
                // 获取玩家名字 - 统一使用传入的玩家名
                final String playerName;
                if (target.getName() != null) {
                    playerName = target.getName();
                } else {
                    // 如果getName()返回null，使用UUID作为后备
                    playerName = target.getUniqueId().toString().substring(0, 8);
                }

                // 统一的头颅设置策略：始终使用玩家名
                // 这样可以确保一致性，类似CMI插件的做法
                meta.setOwner(playerName);
                plugin.getLogger().info("CrazyCrates: 创建头颅: " + playerName + " (统一使用玩家名查询皮肤)");

                // 为CrazyCrates兼容性，使用改进的MiniMessage格式
                Component name = formatToCrazyCratesComponent(sc.getNameTemplate(), playerName);
                meta.displayName(name);

                // 设置描述
                List<Component> lore = sc.getLoreTemplate().stream()
                        .map(line -> formatToCrazyCratesComponent(line, playerName))
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

    private Component formatToCrazyCratesComponent(String template, String playerName) {
        String replaced = template.replace("{Player_name}", playerName);

        // 优化的颜色处理，专为CrazyCrates设计
        String processed = processColorsForCrazyCrates(replaced);

        try {
            Component component = miniMessage.deserialize(processed);
            // 强制禁用斜体装饰（针对lore的特殊处理）
            return component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        } catch (Exception e) {
            // 如果MiniMessage解析失败，回退到简单组件
            plugin.getLogger().warning("CrazyCrates MiniMessage解析失败: " + e.getMessage());
            return Component.text(replaced).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
        }
    }

    private String processColorsForCrazyCrates(String input) {
        // 将 &#RRGGBB 转换为 <#RRGGBB>
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
            // 头颅类型补全
            return configManager.getKeys().stream()
                    .filter(k -> k.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            // 只补全在线玩家，避免性能问题
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3) {
            // 奖池名补全 - 这里可以根据实际情况添加奖池名列表
            return Arrays.asList("example", "basic", "premium", "legendary").stream()
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 4) {
            // 权重建议
            return Arrays.asList("1.0", "5.0", "10.0", "20.0", "50.0");
        }

        if (args.length == 5) {
            // 层级建议
            return Arrays.asList("common", "rare", "epic", "legendary").stream()
                    .filter(tier -> tier.toLowerCase().startsWith(args[4].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
