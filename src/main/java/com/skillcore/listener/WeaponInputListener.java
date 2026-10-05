package com.skillcore.listener;

import com.skillcore.weapon.WeaponItems;
import com.skillcore.weapon.WeaponManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * 技能武器按键监听 — 左键 / 右键 / Shift 组合。
 * <p>
 * <b>技能与武器绑定：</b>只有主手持有对应技能武器（PDC weapon_id）时才会触发技能。
 * 未持武器、普通物品、空手 — 一律无技能。
 */
public final class WeaponInputListener implements Listener {

    private final WeaponManager weaponManager;

    public WeaponInputListener(WeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        // 只处理主手，避免副手重复触发
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) return;

        Player player = event.getPlayer();
        // 必须手持技能武器 — 否则不放技能
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) {
            return;
        }

        boolean sneak = player.isSneaking();
        boolean handled = weaponManager.triggerHeld(player, left, sneak);
        if (handled && event.hasItem()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        // 必须手持技能武器
        if (!WeaponItems.isSkillWeapon(com.skillcore.SkillCorePlugin.getInstance(),
                player.getInventory().getItemInMainHand())) {
            return;
        }
        boolean sneak = player.isSneaking();
        weaponManager.triggerHeld(player, false, sneak);
    }
}
