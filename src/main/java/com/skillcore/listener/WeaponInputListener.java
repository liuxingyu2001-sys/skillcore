package com.skillcore.listener;

import com.skillcore.SkillCorePlugin;
import com.skillcore.weapon.SkillTrigger;
import com.skillcore.weapon.WeaponItems;
import com.skillcore.weapon.WeaponManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能武器输入监听 — 左键 / 右键 / Shift 组合 / 双击 Shift / 按住 Shift / 松开 Shift。
 * <p>
 * <b>技能与武器绑定：</b>只有主手持有对应技能武器（PDC weapon_id）时才会触发技能。
 * 未持武器、普通物品、空手 — 一律无技能。
 */
public final class WeaponInputListener implements Listener {

    private final SkillCorePlugin plugin;
    private final WeaponManager weaponManager;

    /** 双击 / 按住 Shift 状态。 */
    private final Map<UUID, SneakState> sneakStates = new ConcurrentHashMap<>();

    public WeaponInputListener(SkillCorePlugin plugin, WeaponManager weaponManager) {
        this.plugin = plugin;
        this.weaponManager = weaponManager;
    }

    /** 每个玩家的 Shift 状态。 */
    private static final class SneakState {
        long lastSneakDown;
        String weaponId;
        long holdStart;
        boolean holdFired;
        BukkitTask holdTask;
    }

    // ------------------------------------------------------------------
    // 左键 / 右键
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
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
        // triggerHeld 内部已校验是否手持技能武器；未持武器时直接返回 false
        SkillTrigger trigger = triggerOf(left, player.isSneaking());
        boolean handled = weaponManager.triggerHeld(player, trigger);
        if (handled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        // triggerHeld 内部已校验是否手持技能武器
        boolean handled = weaponManager.triggerHeld(player, triggerOf(false, player.isSneaking()));
        if (handled) {
            event.setCancelled(true);
        }
    }

    private SkillTrigger triggerOf(boolean left, boolean sneak) {
        if (left) {
            return sneak ? SkillTrigger.SHIFT_LEFT_CLICK : SkillTrigger.LEFT_CLICK;
        }
        return sneak ? SkillTrigger.SHIFT_RIGHT_CLICK : SkillTrigger.RIGHT_CLICK;
    }

    // ------------------------------------------------------------------
    // Shift：双击 / 按住 / 松开
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        String weaponId = WeaponItems.getHeldWeaponId(plugin, player);
        if (weaponId == null) {
            clearState(player.getUniqueId());
            return;
        }
        SneakState state = sneakStates.computeIfAbsent(player.getUniqueId(), k -> new SneakState());
        long now = System.currentTimeMillis();

        if (event.isSneaking()) {
            onSneakDown(player, state, weaponId, now);
        } else {
            onSneakUp(player, state, weaponId);
        }
    }

    private void onSneakDown(Player player, SneakState state, String weaponId, long now) {
        int windowMs = plugin.getConfigManager().getConfig()
                .getInt("input.double-shift-window-ms", 300);

        boolean doubleTap = state.lastSneakDown > 0
                && weaponId.equals(state.weaponId)
                && (now - state.lastSneakDown) <= windowMs;

        state.lastSneakDown = now;
        state.weaponId = weaponId;

        if (doubleTap) {
            // 双击 Shift：消费掉本次双击，避免三击连触
            state.lastSneakDown = 0;
            weaponManager.triggerHeld(player, SkillTrigger.DOUBLE_SHIFT);
        }

        // 按住 Shift 延迟触发
        state.holdStart = now;
        state.holdFired = false;
        cancelHoldTask(state);
        int delayMs = plugin.getConfigManager().getConfig()
                .getInt("input.hold-shift-delay-ms", 250);
        long delayTicks = Math.max(1L, delayMs / 50L);
        state.holdTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            SneakState current = sneakStates.get(player.getUniqueId());
            if (current != state || !player.isSneaking()) return;
            if (!weaponId.equals(WeaponItems.getHeldWeaponId(plugin, player))) return;
            if (current.holdFired) return;
            if (weaponManager.triggerHeld(player, SkillTrigger.HOLD_SHIFT)) {
                current.holdFired = true;
            }
        }, delayTicks);
    }

    private void onSneakUp(Player player, SneakState state, String weaponId) {
        cancelHoldTask(state);
        boolean started = state.holdStart > 0;
        state.holdStart = 0;
        if (started && state.holdFired && weaponId.equals(state.weaponId)) {
            weaponManager.triggerHeld(player, SkillTrigger.RELEASE_SHIFT);
        }
        state.holdFired = false;
    }

    private void cancelHoldTask(SneakState state) {
        if (state.holdTask != null) {
            state.holdTask.cancel();
            state.holdTask = null;
        }
    }

    // ------------------------------------------------------------------
    // 清理
    // ------------------------------------------------------------------

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        // 换武器时使双击 / 按住状态失效
        clearState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        clearState(id);
        weaponManager.clearPlayerState(id);
    }

    private void clearState(UUID playerId) {
        SneakState state = sneakStates.remove(playerId);
        if (state != null) {
            cancelHoldTask(state);
        }
    }

    /** 插件禁用时清理全部状态。 */
    public void cleanup() {
        for (SneakState state : sneakStates.values()) {
            cancelHoldTask(state);
        }
        sneakStates.clear();
    }
}
