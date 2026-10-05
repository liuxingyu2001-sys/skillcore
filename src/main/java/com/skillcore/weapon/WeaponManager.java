package com.skillcore.weapon;

import com.skillcore.utils.CooldownUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 武器施法管理 — 只有冷却系统，无法力。
 */
public final class WeaponManager {

    private final WeaponRegistry registry;
    private final WeaponFactory factory;
    private boolean debug;

    public WeaponManager(WeaponRegistry registry, WeaponFactory factory) {
        this.registry = registry;
        this.factory = factory;
    }

    public WeaponRegistry registry() { return registry; }
    public WeaponFactory factory() { return factory; }
    public void setDebug(boolean debug) { this.debug = debug; }

    /**
     * 用主手武器触发技能。
     */
    public boolean triggerHeld(Player player, boolean leftClick, boolean sneak) {
        if (player == null) return false;
        ItemStack item = player.getInventory().getItemInMainHand();
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return false;

        WeaponRegistry.Entry entry = registry.getEntry(weaponId).orElse(null);
        if (entry == null) return false;

        WeaponSkill skill = entry.resolve(leftClick);
        if (skill == null) return false;

        return cast(entry.weapon(), skill, player, leftClick, sneak, item);
    }

    /**
     * 完整释放流程（冷却检查 → 执行 → 冷却启动）。
     */
    public boolean cast(
            SkillWeapon weapon,
            WeaponSkill skill,
            Player player,
            boolean leftClick,
            boolean sneak,
            ItemStack item
    ) {
        if (weapon == null || skill == null || player == null) return false;

        LivingEntity target = com.skillcore.utils.AimUtils.raycastEntity(player, weapon.stats().aimRange());
        WeaponContext ctx = new WeaponContext(
                player, weapon, target,
                com.skillcore.utils.AimUtils.getAimLocation(player, weapon.stats().aimRange()),
                item, leftClick, sneak
        );

        if (!skill.canUse(ctx)) {
            return false;
        }

        // 冷却
        String cdKey = cooldownKey(weapon.id(), leftClick, sneak);
        if (!player.hasPermission("skillcore.bypass.cooldown")
                && CooldownUtils.isOnCooldown(player.getUniqueId(), cdKey)) {
            double remain = CooldownUtils.getRemaining(player.getUniqueId(), cdKey);
            MessageUtils.sendActionBar(player, "&c冷却中 &f" + String.format("%.1f", remain) + "s");
            SoundUtils.cooldown(player);
            return true;
        }

        // 执行技能（仅当手持该武器时 WeaponInputListener 才会调到这里）
        try {
            if (leftClick) {
                if (sneak) skill.onShiftLeftClick(ctx);
                else skill.onLeftClick(ctx);
            } else {
                if (sneak) skill.onShiftRightClick(ctx);
                else skill.onRightClick(ctx);
            }
        } catch (Exception ex) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger()
                    .severe("Weapon skill error [" + weapon.id() + "/" + skill.getType() + "]: " + ex);
            ex.printStackTrace();
            return false;
        }

        // 冷却启动（无法力消耗）
        if (!player.hasPermission("skillcore.bypass.cooldown") && weapon.stats().cooldown() > 0) {
            CooldownUtils.start(player.getUniqueId(), cdKey, weapon.stats().cooldown());
        }

        if (debug) {
            com.skillcore.SkillCorePlugin.getInstance().getLogger().info(
                    player.getName() + " used weapon " + weapon.id()
                            + " skill=" + skill.getType() + " left=" + leftClick + " sneak=" + sneak);
        }
        return true;
    }

    private String cooldownKey(String weaponId, boolean leftClick, boolean sneak) {
        String side = leftClick ? "L" : "R";
        String mod = sneak ? "S" : "";
        return "weapon:" + weaponId + ":" + side + mod;
    }

    /**
     * 剩余冷却（秒）。
     */
    public double getCooldownRemaining(Player player, String weaponId, boolean leftClick, boolean sneak) {
        return CooldownUtils.getRemaining(player.getUniqueId(), cooldownKey(weaponId, leftClick, sneak));
    }

    /**
     * 清除冷却。
     */
    public void clearCooldown(Player player, String weaponId) {
        CooldownUtils.clearAll(player.getUniqueId());
    }

    /**
     * 受击时通知武器技能（反伤类）。
     */
    public void notifyDamaged(Player player, LivingEntity attacker, double damage) {
        String weaponId = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        if (weaponId == null) return;
        registry.getEntry(weaponId).ifPresent(entry -> {
            WeaponSkill skill = entry.resolve(false);
            if (skill != null) {
                WeaponContext ctx = new WeaponContext(
                        player, entry.weapon(), attacker,
                        attacker.getLocation(),
                        player.getInventory().getItemInMainHand(),
                        false, false);
                skill.onDamaged(ctx, attacker, damage);
            }
        });
    }

    /**
     * 发放武器给玩家（lore 已填入当前数值）。
     */
    public ItemStack giveWeapon(Player player, String weaponId) {
        SkillWeapon weapon = registry.get(weaponId);
        if (weapon == null) return null;
        ItemStack item = weapon.toItemStack();
        player.getInventory().addItem(item);
        return item;
    }

    /**
     * 刷新玩家背包中所有指定武器的 lore（管理员改配置后手动更新）。
     *
     * @return 更新数量
     */
    public int refreshLore(Player player, String weaponId) {
        if (player == null) return 0;
        SkillWeapon weapon = registry.get(weaponId);
        if (weapon == null) return 0;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        int updated = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id != null && id.equalsIgnoreCase(weaponId)) {
                if (WeaponItems.updateLore(item, weapon)) updated++;
            }
        }
        // 盔甲等
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id != null && id.equalsIgnoreCase(weaponId)) {
                if (WeaponItems.updateLore(item, weapon)) updated++;
            }
        }
        player.updateInventory();
        return updated;
    }

    /**
     * 刷新玩家背包内全部技能武器的 lore。
     */
    public int refreshAllLore(Player player) {
        if (player == null) return 0;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        int updated = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String id = WeaponItems.getWeaponId(plugin, item);
            if (id == null) continue;
            SkillWeapon weapon = registry.get(id);
            if (weapon != null && WeaponItems.updateLore(item, weapon)) {
                updated++;
            }
        }
        player.updateInventory();
        return updated;
    }

    /**
     * 刷新所有在线玩家身上指定武器的 lore。
     */
    public int refreshLoreAllPlayers(String weaponId) {
        int total = 0;
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            total += refreshLore(p, weaponId);
        }
        return total;
    }

    /**
     * 手持的是否是指定武器。
     */
    public boolean isHolding(Player player, String weaponId) {
        String held = WeaponItems.getHeldWeaponId(
                com.skillcore.SkillCorePlugin.getInstance(), player);
        return held != null && held.equalsIgnoreCase(weaponId);
    }
}
