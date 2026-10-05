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
        if (percent <= 0) {
            lifestealPercent.remove(player.getUniqueId());
        } else {
            lifestealPercent.put(player.getUniqueId(), percent);
        }
    }

    public double getLifesteal(Player player) {
        return lifestealPercent.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setReflect(Player player, double percent) {
        if (percent <= 0) {
            reflectPercent.remove(player.getUniqueId());
        } else {
            reflectPercent.put(player.getUniqueId(), percent);
        }
    }

    public double getReflect(Player player) {
        return reflectPercent.getOrDefault(player.getUniqueId(), 0.0);
    }

    // ------------------------------------------------------------------
    // Damage events
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity damager = DamageUtils.getDamager(event);
        LivingEntity victim = DamageUtils.getVictim(event);
        if (damager == null || victim == null) {
            return;
        }

        double finalDamage = event.getFinalDamage();

        // --- lifesteal ---
        if (damager instanceof Player player) {
            double percent = getLifesteal(player);
            // also check buff style
            if (percent > 0) {
                double healed = LifestealUtils.healByDamagePercent(player, finalDamage, percent);
                if (healed > 0 && plugin.getConfig().getBoolean("logging.log-damage-calc", false)) {
                    plugin.getLogger().info(String.format(
                            "Lifesteal %s -> %s damage=%.2f healed=%.2f",
                            player.getName(), victim.getName(), finalDamage, healed));
                }
            }
            // 武器 onHit / onDamaged 钩子
            if (plugin.getWeaponManager() != null) {
                // 受击方若是玩家且持技能武器
                if (victim instanceof Player victimPlayer) {
                    plugin.getWeaponManager().notifyDamaged(victimPlayer, damager, finalDamage);
                }
            }
        }

        // --- reflect / thorns ---
        double reflectPercentValue = 0.0;
        double reflectFlat = 0.0;
        if (victim instanceof Player player) {
            reflectPercentValue = getReflect(player);
        }
        ReflectDamageUtils.ReflectBuff buff = ReflectDamageUtils.getReflectBuff(victim);
        if (buff != null) {
            reflectPercentValue = Math.max(reflectPercentValue, buff.percent());
            reflectFlat = buff.flat();
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
        if (!plugin.getConfig().getBoolean("logging.log-damage-calc", false)) {
            return;
        }
        LivingEntity victim = DamageUtils.getVictim(event);
        if (victim == null) {
            return;
        }
        plugin.getLogger().info(String.format(
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
}
