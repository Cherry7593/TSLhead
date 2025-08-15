// src/main/java/com/zvbj/TSL_head/AnvilListener.java
package com.zvbj.TSL_head;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public class AnvilListener implements Listener {
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack item = inv.getItem(0);
        if (item != null && item.getType() == Material.PLAYER_HEAD) {
            event.setResult(null); // 禁止所有头颅被铁砧修改
        }
    }
}