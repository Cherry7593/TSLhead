package com.zvbj.TSL_head;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import java.util.stream.Collectors;

public class HeadCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final TSL_Head plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

            // Folia适配：使用异步任务处理头颅获取
            CompletableFuture.supplyAsync(() -> {
                // 使用OfflinePlayer来支持离线玩家
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerName);

                // 检查目标玩家是否曾经进过服务器
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    // Folia环境下使用区域调度器，传统环境使用Bukkit调度器
                    scheduleSync(receiver, () -> {
                        sender.sendMessage(Component.text("玩家 " + targetPlayerName + " 从未进入过服务器", NamedTextColor.RED));
                    });
                    return null;
                }

                // 创建头颅物品
                return createSkull(target, sc, targetPlayerName);
            }).thenAccept(skull -> {
                if (skull == null) {
                    // 切换回主线程发送错误消息
                    scheduleSync(receiver, () -> {
                        sender.sendMessage(Component.text("创建头颅失败", NamedTextColor.RED));
                    });
                    return;
                }

                // 切换回主线程执行物品操作
                scheduleSync(receiver, () -> {
                    giveSkullToPlayer(sender, receiver, skull, targetPlayerName, key);
                });
            }).exceptionally(throwable -> {
                plugin.getLogger().warning("异步处理头颅时发生错误: " + throwable.getMessage());
                return null;
            });

            return true;
        }

        sender.sendMessage(Component.text("用法: /tslhead reload OR /tslhead <命名> <玩家名> [接收玩家]", NamedTextColor.YELLOW));
        return true;
    }

    private void scheduleSync(Player player, Runnable task) {
        if (isFolia()) {
            player.getScheduler().run(plugin, (scheduledTask) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
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

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private @Nullable ItemStack createSkull(OfflinePlayer target, SkullConfig sc, String fallbackName) {
        try {
            ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                // 使用UUID设置头颅所有者，这样即使玩家离线也能正确显示皮肤
                meta.setOwningPlayer(target);

                // 获取玩家名字（优先使用真实名字，如果离线则使用输入的名字）
                String playerName = target.getName() != null ? target.getName() : fallbackName;

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
            return null;
        }
    }

    private Component formatToComponent(String template, String playerName) {
        String replaced = template.replace("{Player_name}", playerName);
        String withHex = applyHexColors(replaced);
        return LegacyComponentSerializer.legacyAmpersand().deserialize(withHex);
    }

    private String applyHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            String legacy = toLegacy(hex);
            matcher.appendReplacement(sb, legacy);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String toLegacy(String hex) {
        char[] chars = hex.toCharArray();
        StringBuilder sb = new StringBuilder("§x");
        for (char c : chars) {
            sb.append('§').append(c);
        }
        return sb.toString();
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

