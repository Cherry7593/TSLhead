package com.zvbj.TSL_head;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

public class CrazyCratesCommand implements CommandExecutor, TabCompleter {
    private final ConfigManager configManager;
    private final TSL_Head plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

        // Folia适配：使用CompletableFuture异步处理头颅创建和添加
        CompletableFuture.supplyAsync(() -> {
            // 检查目标玩家
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                // 切换回主线程发送消息
                scheduleSync(player, () -> {
                    sender.sendMessage(Component.text("玩家 " + playerName + " 从未进入过服务器", NamedTextColor.RED));
                });
                return null;
            }

            // 创建头颅物品
            return createSkull(target, sc);
        }).thenAccept(skull -> {
            if (skull == null) {
                // 切换回主线程发送错误消息
                scheduleSync(player, () -> {
                    sender.sendMessage(Component.text("创建头颅失败", NamedTextColor.RED));
                });
                return;
            }

            // 切换到主线程执行CrazyCrates命令
            scheduleSync(player, () -> {
                executeAddToCrate(sender, player, skull, skullType, playerName, crateName, weight, tier);
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("异步处理CrazyCrates命令时发生错误: " + throwable.getMessage());
            return null;
        });

        return true;
    }

    private void scheduleSync(Player player, Runnable task) {
        if (isFolia()) {
            player.getScheduler().run(plugin, (scheduledTask) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
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

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private @Nullable ItemStack createSkull(@NotNull OfflinePlayer target, @NotNull SkullConfig sc) {
        try {
            ItemStack skull = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();

            if (meta != null) {
                // 使用UUID设置头颅所有者
                meta.setOwningPlayer(target);

                // 获取玩家名字 - 修复逻辑错误并声明为final
                final String playerName;
                if (target.getName() != null) {
                    playerName = target.getName();
                } else {
                    // 如果名字为null，使用UUID的字符串形式作为后备
                    playerName = target.getUniqueId().toString().substring(0, 8);
                }

                // 为CrazyCrates兼容性，使用MiniMessage格式
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
            return null;
        }
    }

    private Component formatToCrazyCratesComponent(String template, String playerName) {
        String replaced = template.replace("{Player_name}", playerName);

        // 为CrazyCrates生成特殊的MiniMessage格式
        String crazyFormat = convertToCrazyCratesFormat(replaced);

        try {
            return MiniMessage.miniMessage().deserialize(crazyFormat);
        } catch (Exception e) {
            // 如果MiniMessage解析失败，回退到传统格式
            plugin.getLogger().warning("MiniMessage解析失败，回退到传统格式: " + e.getMessage());
            return LegacyComponentSerializer.legacyAmpersand().deserialize(replaced);
        }
    }

    private String convertToCrazyCratesFormat(String input) {
        // 先处理十六进制颜色代码 &#RRGGBB -> <#RRGGBB>
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

        // 转换为CrazyCrates特有的格式
        return generateCrazyCratesFormat(result);
    }

    private String generateCrazyCratesFormat(String input) {
        // CrazyCrates期望的格式：<!italic><!underlined><!strikethrough><!bold><!obfuscated><content></!obfuscated></!bold></!strikethrough></!underlined></!italic><!italic>

        StringBuilder sb = new StringBuilder();

        // 添加开头的否定格式标记
        sb.append(" <!italic><!underlined><!strikethrough><!bold><!obfuscated>");

        // 分析输入内容，找到第一个颜色标记
        if (input.contains("<gray>") || input.contains("<white>") || input.contains("<#")) {
            // 找到第一个颜色标记的位置
            int colorStart = findFirstColorTag(input);

            if (colorStart != -1) {
                // 提取颜色标记前的内容
                String beforeColor = input.substring(0, colorStart);
                String remaining = input.substring(colorStart);

                // 添加前缀内容
                sb.append(beforeColor);

                // 处理颜色标记和后续内容
                processColoredContent(sb, remaining);
            } else {
                // 没有找到颜色标记，直接添加
                sb.append(input);
            }
        } else {
            // 没有颜色标记，直接添加
            sb.append(input);
        }

        return sb.toString();
    }

    private int findFirstColorTag(String input) {
        // 查找第一个颜色标记的位置
        String[] colorTags = {"<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>",
                             "<dark_red>", "<dark_purple>", "<gold>", "<gray>",
                             "<dark_gray>", "<blue>", "<green>", "<aqua>",
                             "<red>", "<light_purple>", "<yellow>", "<white>", "<#"};

        int earliest = Integer.MAX_VALUE;
        for (String tag : colorTags) {
            int pos = input.indexOf(tag);
            if (pos != -1 && pos < earliest) {
                earliest = pos;
            }
        }

        return earliest == Integer.MAX_VALUE ? -1 : earliest;
    }

    private void processColoredContent(StringBuilder sb, String content) {
        // 处理包含颜色的内容
        // 如果是灰色开始，按照CrazyCrates的模式处理
        if (content.startsWith("<gray>")) {
            // 找到</gray>的位置
            int grayEnd = content.indexOf("</gray>");
            if (grayEnd != -1) {
                String grayContent = content.substring(0, grayEnd + 7); // 包含</gray>
                String afterGray = content.substring(grayEnd + 7);

                sb.append(grayContent);
                sb.append("</!obfuscated></!bold></!strikethrough></!underlined></!italic><!italic>");
                sb.append(afterGray);
            } else {
                sb.append(content);
            }
        } else {
            // 其他颜色的处理
            sb.append(content);
        }
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
