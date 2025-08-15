package com.zvbj.TSL_head;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HeadCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public HeadCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("tslhead.use")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            configManager.loadConfigs();
            sender.sendMessage(ChatColor.GREEN + "TSLhead 配置已重新加载");
            return true;
        }

        // 支持2个参数（默认给自己）或3个参数的情况
        if (args.length == 2 || args.length == 3) {
            String key = args[0];
            SkullConfig sc = configManager.getConfig(key);
            if (sc == null) {
                sender.sendMessage(ChatColor.RED + "未找到命名: " + key);
                return true;
            }

            // 使用OfflinePlayer来支持离线玩家
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            Player receiver;

            // 如果只有2个参数，接收者默认为命令发送者
            if (args.length == 2) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "控制台必须指定接收玩家");
                    return true;
                }
                receiver = (Player) sender;
            } else {
                // 3个参数的情况，使用指定的接收者
                receiver = Bukkit.getPlayerExact(args[2]);
            }

            // 检查目标玩家是否曾经进过服务器
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "玩家 " + args[1] + " 从未进入过服务器");
                return true;
            }

            // 检查接收者是否在线
            if (receiver == null) {
                sender.sendMessage(ChatColor.RED + "接收玩家 " + args[2] + " 不在线");
                return true;
            }

            // 创建头颅物品
            ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                // 使用UUID设置头颅所有者，这样即使玩家离线也能正确显示皮肤
                meta.setOwningPlayer(target);

                // 获取玩家名字（优先使用真实名字，如果离线则使用输入的名字）
                String playerName = target.getName() != null ? target.getName() : args[1];

                String name = format(sc.getNameTemplate(), playerName);
                meta.setDisplayName(name);
                List<String> lore = sc.getLoreTemplate().stream()
                        .map(line -> format(line, playerName))
                        .collect(Collectors.toList());
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }

            // 尝试将头颅添加到玩家背包，如果背包满了则掉落到地上
            HashMap<Integer, ItemStack> leftover = receiver.getInventory().addItem(skull);

            // 检查是否有剩余物品（背包满了）
            if (!leftover.isEmpty()) {
                // 背包满了，将头颅掉落到玩家脚下
                for (ItemStack item : leftover.values()) {
                    receiver.getWorld().dropItemNaturally(receiver.getLocation(), item);
                }
                sender.sendMessage(ChatColor.YELLOW + "玩家 " + receiver.getName() + " 的背包已满，头颅已掉落到其脚下");
            }

            String targetStatus = target.isOnline() ? "在线" : "离线";
            sender.sendMessage(ChatColor.GREEN + "已将 " + args[1] + " (" + targetStatus + ") 的头颅 (" + key + ") 给予 " + receiver.getName());
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "用法: /thead reload OR /thead <命名> <玩家名> [接收玩家]");
        return true;
    }

    private String format(String template, String playerName) {
        String replaced = template.replace("{Player_name}", playerName);
        String withHex = applyHexColors(replaced);
        return ChatColor.translateAlternateColorCodes('&', withHex);
    }

    private String applyHexColors(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
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
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("reload"));
            completions.addAll(configManager.getKeys().stream()
                    .filter(k -> k.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList()));
            return completions;
        }

        if (args.length == 2) {
            // 为目标玩家提供补全：在线玩家 + 最近离线的玩家
            List<String> suggestions = new ArrayList<>();

            // 添加在线玩家
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList()));

            // 添加离线玩家（限制数量避免过多建议）
            suggestions.addAll(Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.hasPlayedBefore())
                    .map(OfflinePlayer::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .limit(10) // 限制离线玩家建议数量
                    .collect(Collectors.toList()));

            return suggestions.stream().distinct().collect(Collectors.toList());
        }

        if (args.length == 3) {
            // 接收玩家只能是在线玩家
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}