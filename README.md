# TSLhead - TSL服务器专用头颅插件（Folia适配版）

一个功能强大的Minecraft服务器头颅插件，支持获取在线和离线玩家头颅，以及与CrazyCrates抽奖插件的无缝联动。**完全兼容Folia服务端！**

## ✨ 主要功能

- 🎭 **获取玩家头颅**：支持获取在线和离线玩家的头颅（使用UUID，永远稳定）
- 🎒 **智能背包管理**：背包满时自动掉落物品，不会丢失
- ⚡ **异步处理**：头颅获取异步进行，不影响服务器性能
- 🌟 **Folia完全兼容**：针对Folia服务端进行了专门优化，支持区域调度
- 🎁 **CrazyCrates联动**：一键将头颅添加到抽奖池
- 🎨 **自定义模板**：支持多种头颅类型和自定义样式
- 🌈 **十六进制颜色**：支持现代颜色代码 (&#RRGGBB)
- 📝 **智能补全**：优化的Tab补全，只显示在线玩家避免卡顿

## 📦 安装要求

- **Minecraft版本**：1.16+
- **服务端**：Paper/Spigot/Bukkit/Folia
- **Java版本**：Java 21+（Folia要求）
- **可选依赖**：CrazyCrates（用于抽奖联动功能）

## 🚀 Folia适配特性

本插件经过专门适配，完全支持Folia服务端的区域调度系统：

- **智能服务端检测**：自动检测是否运行在Folia环境
- **区域调度器支持**：在Folia环境下使用`Player.getScheduler()`进行区域调度
- **向下兼容**：在传统Paper/Spigot环境下自动使用`BukkitScheduler`
- **异步优化**：使用`CompletableFuture`进行异步任务处理，完美适配Folia的线程模型

### Folia调度器原理

```java
// 自动检测Folia环境
private boolean isFolia() {
    try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}

// 智能调度器切换
if (isFolia()) {
    player.getScheduler().run(plugin, (task) -> {
        // Folia区域调度器
    }, null);
} else {
    Bukkit.getScheduler().runTask(plugin, () -> {
        // 传统Bukkit调度器
    });
}
```

## 🎮 命令使用

### 基础头颅命令

```
/tslhead <头颅类型> <玩家名> [接收玩家]
```

**示例：**
```bash
# 给自己获取example类型的Steve头颅
/tslhead example Steve

# 给指定玩家获取memorial类型的Alice头颅  
/tslhead memorial Alice Bob

# 重载配置
/tslhead reload
```

**别名：** `/thead`

### CrazyCrates联动命令

```
/tslheadcc <头颅类型> <玩家名> <奖池名> <权重> [层级]
```

**示例：**
```bash
# 将Steve的memorial头颅添加到premium奖池，权重10.0
/tslheadcc memorial Steve premium 10.0

# 添加到指定层级
/tslheadcc vip Alice legendary 50.0 epic
```

**别名：** `/theadcc`

## ⚙️ 配置文件

### config.yml

```yaml
# 示例头颅配置
example:
  name: "&6{Player_name} &7的头颅"
  lore:
    - "&7这是 &6{Player_name} &7的头颅"
    - "&8右键点击查看详情"

# 纪念头颅配置
memorial:
  name: "&#aaf6c0&l{Player_name} &7♥"
  lore:
    - "&#aaf6c0▩▩▩ TSL的新生力量 ▩▩▩"
    - "&#d0ffde「这颗头颅见证了{Player_name}在服务器世界中的足迹」"
    - "&#d0ffde「愿它永远守护你在TSL的回忆」"
    - "&8 ┃ &7描述在头颅放置后会消失哦 &8┃"

# VIP头颅配置  
vip:
  name: "&b&l[VIP] &f{Player_name}"
  lore:
    - "&b✦ VIP专属头颅 ✦"
    - "&7玩家: &f{Player_name}"
    - "&8限量收藏版"

# 管理员头颅配置
admin:
  name: "&c&l[ADMIN] &4{Player_name}"
  lore:
    - "&c⚡ 管理员专属头颅 ⚡"
    - "&7玩家: &f{Player_name}"
    - "&4权威象征"
```

### 配置说明

- **{Player_name}**：玩家名占位符，会自动替换为实际玩家名
- **颜色代码**：支持传统&代码和现代十六进制颜色 (&#RRGGBB)
- **格式代码**：&l(粗体)、&o(斜体)、&n(下划线)等

## 🔐 权限系统

| 权限节点 | 描述 | 默认值 |
|---------|------|--------|
| `tslhead.use` | 使用基础头颅命令 | `true` |
| `tslhead.crazycrates` | 使用CrazyCrates联动 | `op` |
| `tslhead.admin` | 管理员权限（包含所有权限） | `op` |

## 🎯 特色功能详解

### 1. 离线玩家支持
- 使用UUID获取头颅，即使玩家离线也能正确显示皮肤
- 只要玩家曾经进入过服务器，就能获取其头颅
- 自动检测玩家是否存在于服务器缓存中

### 2. Folia性能优化
- **区域调度器**：在Folia环境下使用玩家所在区域的调度器
- **异步处理**：头颅获取在后台线程进行，不阻塞主线程
- **智能Tab补全**：只补全在线玩家，避免服务器卡顿
- **错误处理**：完善的异常处理机制，确保插件稳定运行

### 3. 智能背包管理
```java
// 当玩家背包满时
if (!leftover.isEmpty()) {
    // 头颅会掉落到玩家脚下，而不是消失
    receiver.getWorld().dropItemNaturally(receiver.getLocation(), item);
    sender.sendMessage("背包已满，头颅已掉落到脚下");
}
```

### 4. CrazyCrates深度集成
- 自动将头颅添加到指定奖池
- 支持自定义权重和层级
- 临时手持物品机制，确保添加成功
- 智能奖池名补全
- Folia环境下的安全执行

## 🔧 故障排除

### 常见问题

**Q: 在Folia服务端上是否需要特殊配置？**  
A: 不需要！插件会自动检测Folia环境并使用相应的调度器。确保使用Java 21+。

**Q: 为什么获取不到某个玩家的头颅？**  
A: 确保该玩家至少进入过服务器一次。插件使用服务器缓存的UUID/Profile数据。

**Q: Tab补全很慢怎么办？**  
A: 新版本已优化，只补全在线玩家。在Folia环境下性能更佳。

**Q: CrazyCrates提示"不能添加空气"？**  
A: 这通常是权限问题或CrazyCrates版本兼容性问题。确保玩家有执行crazycrates命令的权限。

**Q: 头颅显示史蒂夫皮肤？**  
A: 检查网络连接，或等待几分钟让皮肤服务器响应。离线玩家首次获取可能需要时间。

### 日志调试

插件会在控制台输出详细日志：
```
[TSLhead] 创建头颅时发生错误: [具体错误信息]
[TSLhead] 异步处理头颅时发生错误: [具体错误信息]
[TSLhead] TSLhead 已启用
```

## 🏗️ 开发者信息

### 技术栈
- **Java 21**：支持最新的语言特性
- **Paper API 1.21.4**：使用最新的Paper API
- **CompletableFuture**：现代异步编程
- **Folia区域调度器**：专为Folia优化

### 架构特点
- **双调度器支持**：自动适配Folia和传统Bukkit环境
- **异步优先**：所有耗时操作都在异步线程执行
- **区域安全**：在Folia环境下确保所有操作都在正确的区域执行

## 📈 更新日志

### v1.0 - Folia适配版
- ✅ 完全支持Folia服务端
- ✅ 智能服务端环境检测
- ✅ 区域调度器适配
- ✅ CompletableFuture异步处理
- ✅ 基础头颅获取功能
- ✅ 离线玩家支持
- ✅ CrazyCrates联动
- ✅ 智能Tab补全优化
- ✅ 十六进制颜色支持
- ✅ 智能背包管理

## 🤝 支持与反馈

如有问题或建议，请联系插件作者或在服务器论坛发帖。

### 兼容性测试

| 服务端类型 | 版本 | 状态 |
|-----------|------|------|
| Folia | 最新版 | ✅ 完全支持 |
| Paper | 1.21.4+ | ✅ 完全支持 |
| Paper | 1.16-1.21.3 | ✅ 向下兼容 |
| Spigot | 1.16+ | ✅ 基础支持 |
| Bukkit | 1.16+ | ✅ 基础支持 |

---

**插件作者：** ZVBJ  
**版本：** 1.0 (Folia适配版)  
**适用于：** TSL服务器 & Folia环境  
**技术支持：** 完整的Folia区域调度器支持
