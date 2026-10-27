package com.skillcore.listener;

import com.skillcore.SkillCorePlugin;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.ReflectDamageUtils;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Combat event listener for lifesteal, reflect, and combat utility hooks.
 * Passive skill effects plug into these events.
 */
public final class CombatListener implements Listener {

    private final SkillCorePlugin plugin;

    /**
     * Per-player default lifesteal percent (can be driven by equipment / buffs later).
     */
    private final java.util.Map<java.util.UUID, Double> lifestealPercent = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Per-player default reflect percent.
     */
    private final java.util.Map<java.util.UUID, Double> reflectPercent = new java.util.concurrent.ConcurrentHashMap<>();

    public CombatListener(SkillCorePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Per-player combat modifiers
    // ------------------------------------------------------------------

    public void setLifesteal(Player player, double percent) {
        if (player == null) return;
        if (!Double.isFinite(percent) || percent <= 0) {
            lifestealPercent.remove(player.getUniqueId());
        } else {
            lifestealPercent.put(player.getUniqueId(), Math.min(percent, 1.0));
        }
    }

    public double getLifesteal(Player player) {
        return player == null ? 0.0 : lifestealPercent.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setReflect(Player player, double percent) {
        if (player == null) return;
        if (!Double.isFinite(percent) || percent <= 0) {
            reflectPercent.remove(player.getUniqueId());
        } else {
            reflectPercent.put(player.getUniqueId(), Math.min(percent, 1.0));
        }
    }

    public double getReflect(Player player) {
        return player == null ? 0.0 : reflectPercent.getOrDefault(player.getUniqueId(), 0.0);
    }

    // ------------------------------------------------------------------
    // Damage events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity damager = DamageUtils.getDamager(event);
        LivingEntity victim = DamageUtils.getVictim(event);
        if (damager == null || victim == null || damager.equals(victim)) {
            return;
        }

        // --- 盔甲套装被动：穿戴整套者增伤 / 减伤（先于最终伤害计算） ---
        com.skillcore.armor.ArmorManager armorManager = plugin.getArmorManager();
        com.skillcore.armor.ArmorStats attackerArmor = null;
        com.skillcore.armor.ArmorStats defenderArmor = null;
        if (armorManager != null) {
            if (damager instanceof Player attacker) {
                attackerArmor = armorManager.getActive(attacker);
                double mult = attackerArmor.damageMultiplier();
                if (mult > 0 && Math.abs(mult - 1.0) > 1.0e-9) {
                    event.setDamage(event.getDamage() * mult);
                }
            }
            if (victim instanceof Player defender) {
                defenderArmor = armorManager.getActive(defender);
                double reduction = defenderArmor.damageReduction();
                if (reduction > 0) {
                    event.setDamage(event.getDamage() * (1.0 - Math.min(reduction, 1.0)));
                }
            }
        }

        double finalDamage = event.getFinalDamage();

        // --- 受击钩子：受击方是玩家就触发（攻击者可以是玩家 / 怪物 / 投射物） ---
        // 反伤类技能依赖这条路径，必须放在“攻击者是玩家”的判断之外，
        // 否则被怪物攻击时永远不会触发。
        if (plugin.getWeaponManager() != null && victim instanceof Player victimPlayer) {
            plugin.getWeaponManager().notifyDamaged(victimPlayer, damager, finalDamage);
        }

        // --- lifesteal ---
        // 技能伤害的吸血由 WeaponContext 统一处理（武器 lifesteal 与盔甲 lifesteal 取较大值），
        // 这里只处理普通伤害，否则同一次技能命中会吸两次血。
        if (damager instanceof Player player && !com.skillcore.utils.SkillDamageUtils.isSkillDamage(victim)) {
            double percent = getLifesteal(player);
            // 叠加盔甲套装吸血（取较大值，避免与其他来源重复叠加）
            if (attackerArmor != null) {
                percent = Math.max(percent, attackerArmor.lifesteal());
            }
            if (percent > 0) {
                double healed = LifestealUtils.healByDamagePercent(player, finalDamage, percent);
                if (healed > 0 && plugin.getConfigManager().getConfig().getBoolean("logging.log-damage-calc", false)) {
                    plugin.getLogger().info(String.format(java.util.Locale.ROOT,
                            "Lifesteal %s -> %s damage=%.2f healed=%.2f",
                            player.getName(), victim.getName(), finalDamage, healed));
                }
            }
        }

        // 武器命中钩子：只对「玩家直接近战」触发（普通攻击附带效果）；
        // 技能伤害已标记跳过，避免重复触发。
        // 箭 / 三叉戟 / TNT 的伤害者会被还原成玩家，但那不是近战，不能算命中附带。
        if (damager instanceof Player player
                && event.getDamager() instanceof Player
                && plugin.getWeaponManager() != null
                && !com.skillcore.utils.SkillDamageUtils.isSkillDamage(victim)) {
            plugin.getWeaponManager().notifyHit(player, victim, finalDamage);
        }

        // --- reflect / thorns ---
        double reflectPercentValue = 0.0;
        double reflectFlat = 0.0;
        if (victim instanceof Player player) {
            reflectPercentValue = getReflect(player);
            // 叠加盔甲套装反伤
            if (defenderArmor != null) {
                reflectPercentValue = Math.max(reflectPercentValue, defenderArmor.reflectPercent());
                reflectFlat = Math.max(reflectFlat, defenderArmor.reflectFlat());
            }
        }
        ReflectDamageUtils.ReflectBuff buff = ReflectDamageUtils.getReflectBuff(victim);
        if (buff != null) {
            reflectPercentValue = Math.max(reflectPercentValue, buff.percent());
            reflectFlat = Math.max(reflectFlat, buff.flat());
        }
        if (reflectPercentValue > 0 || reflectFlat > 0) {
            double reflected = ReflectDamageUtils.reflect(
                    victim, damager, finalDamage, reflectPercentValue, reflectFlat);
            if (reflected > 0) {
                ParticleUtils.hitMarker(damager, Particle.CRIT);
            }
        }
    }

    /**
     * Generic damage cleanup / logging hook.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent event) {
        if (!plugin.getConfigManager().getConfig().getBoolean("logging.log-damage-calc", false)) {
            return;
        }
        LivingEntity victim = DamageUtils.getVictim(event);
        if (victim == null) {
            return;
        }
        plugin.getLogger().info(String.format(java.util.Locale.ROOT,
                "Damage %s cause=%s raw=%.2f final=%.2f",
                victim.getName(), event.getCause(),
                event.getDamage(), event.getFinalDamage()));
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lifestealPercent.remove(event.getPlayer().getUniqueId());
        reflectPercent.remove(event.getPlayer().getUniqueId());
        ReflectDamageUtils.removeReflectBuff(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        ReflectDamageUtils.removeReflectBuff(event.getEntity());
    }

    /** 击杀钩子：通知击杀者手持武器的 onKill。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || plugin.getWeaponManager() == null) {
            return;
        }
        plugin.getWeaponManager().notifyKill(killer, victim);
    }
}
