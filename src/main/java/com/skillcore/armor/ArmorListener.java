package com.skillcore.armor;

import com.skillcore.SkillCorePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 盔甲穿戴监听 — 背包变动后下一 tick 重算该玩家的整套加成。
 * <p>
 * 另有一个周期任务兜底（见 {@link SkillCorePlugin} 的 armor ticker）。
 */
public final class ArmorListener implements Listener {

    private final SkillCorePlugin plugin;
    private final ArmorManager manager;

    public ArmorListener(SkillCorePlugin plugin, ArmorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void scheduleRecompute(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> manager.recompute(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRecompute(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRecompute(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // 手持盔甲右键直接穿戴通常不走背包点击事件，这里兜底重算
        if (event.getItem() != null && isArmorType(event.getItem().getType())) {
            scheduleRecompute(event.getPlayer());
        }
    }

    private boolean isArmorType(Material material) {
        if (material == null) {
            return false;
        }
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleRecompute(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.clearPlayer(event.getPlayer().getUniqueId());
    }
}
