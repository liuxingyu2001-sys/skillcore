package com.skillcore.utils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Central damage calculation toolkit.
 * <p>
 * Supports: base damage, scaling, critical hits, armor / toughness reduction,
 * armor penetration, damage multipliers, invulnerability checks, and safe
 * application through Bukkit damage events.
 */
public final class DamageUtils {

    /** Vanilla armor reduction constant (1 armor = 4% reduction up to 80%). */
    private static final double ARMOR_CONSTANT = 4.0;
    private static final double MAX_ARMOR_REDUCTION = 0.80;

    private DamageUtils() {
    }

    // ------------------------------------------------------------------
    // Attribute helpers
    // ------------------------------------------------------------------

    public static double getAttributeValue(LivingEntity entity, Attribute attribute) {
        if (entity == null || attribute == null) {
            return 0.0;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0 : instance.getValue();
    }

    public static double getMaxHealth(LivingEntity entity) {
        return getAttributeValue(entity, Attribute.MAX_HEALTH);
    }

    public static double getMissingHealth(LivingEntity entity) {
        return Math.max(0.0, getMaxHealth(entity) - entity.getHealth());
    }

    public static double getMissingHealthPercent(LivingEntity entity) {
        double max = getMaxHealth(entity);
        return max <= 0 ? 0.0 : getMissingHealth(entity) / max;
    }

    public static double getHealthPercent(LivingEntity entity) {
        double max = getMaxHealth(entity);
        return max <= 0 ? 0.0 : entity.getHealth() / max;
    }

    public static double getAttackDamage(LivingEntity entity) {
        return getAttributeValue(entity, Attribute.ATTACK_DAMAGE);
    }

    public static double getArmor(LivingEntity entity) {
        return getAttributeValue(entity, Attribute.ARMOR);
    }

    public static double getArmorToughness(LivingEntity entity) {
        return getAttributeValue(entity, Attribute.ARMOR_TOUGHNESS);
    }

    // ------------------------------------------------------------------
    // Raw / formula damage
    // ------------------------------------------------------------------

    /**
     * Formula damage with optional scaling: {@code base + level * scaling}.
     */
    public static double formula(double base, double scaling, double level) {
        return base + scaling * level;
    }

    /**
     * Critical hit roll.
     *
     * @param chance 0.0 - 1.0
     */
    public static boolean rollCritical(double chance) {
        return Math.random() < Math.max(0.0, Math.min(1.0, chance));
    }

    /**
     * Apply critical multiplier if roll succeeds.
     */
    public static double applyCritical(double damage, double chance, double criticalMultiplier) {
        return rollCritical(chance) ? damage * criticalMultiplier : damage;
    }

    /**
     * Full damage pipeline used by most attack skills.
     *
     * @param baseDamage          raw skill damage
     * @param scaling             damage per power/level
     * @param power               power / level value
     * @param criticalChance      0.0 - 1.0
     * @param criticalMultiplier  e.g. 1.5
     * @param armorPenetration    0.0 - 1.0 (1.0 = ignore all armor)
     * @param defender            target living entity (may be null for pure calc)
     * @return final damage after armor reduction
     */
    public static double calculateFinalDamage(
            double baseDamage,
            double scaling,
            double power,
            double criticalChance,
            double criticalMultiplier,
            double armorPenetration,
            LivingEntity defender
    ) {
        double damage = formula(baseDamage, scaling, power);
        damage = applyCritical(damage, criticalChance, criticalMultiplier);
        return applyArmorReduction(damage, armorPenetration, defender);
    }

    /**
     * Vanilla-like armor reduction with optional penetration.
     *
     * @param armorPenetration 0.0 - 1.0, reduces effective armor
     */
    public static double applyArmorReduction(double damage, double armorPenetration, LivingEntity defender) {
        if (defender == null || damage <= 0) {
            return Math.max(0.0, damage);
        }
        double armor = getArmor(defender) * (1.0 - clamp(armorPenetration, 0.0, 1.0));
        double toughness = getArmorToughness(defender) * (1.0 - clamp(armorPenetration, 0.0, 1.0));
        return applyArmorFormula(damage, armor, toughness);
    }

    /**
     * Minecraft armor formula (1.9+).
     */
    public static double applyArmorFormula(double damage, double armor, double toughness) {
        if (damage <= 0) {
            return 0.0;
        }
        double reduction = armor - (damage / (toughness + ARMOR_CONSTANT));
        reduction = Math.min(20.0, Math.max(0.0, reduction));
        double multiplier = 1.0 - (reduction / 25.0); // 1 armor = 4%
        multiplier = Math.max(1.0 - MAX_ARMOR_REDUCTION, multiplier);
        return damage * multiplier;
    }

    /**
     * Percent of target's max health as damage (before armor).
     */
    public static double percentOfMaxHealth(LivingEntity target, double percent) {
        return getMaxHealth(target) * percent;
    }

    /**
     * Percent of target's current health as damage (before armor).
     */
    public static double percentOfCurrentHealth(LivingEntity target, double percent) {
        return target.getHealth() * percent;
    }

    /**
     * Percent of target's missing health as damage (execute-style).
     */
    public static double percentOfMissingHealth(LivingEntity target, double percent) {
        return getMissingHealth(target) * percent;
    }

    /**
     * Linear interpolation damage between min and max by power (0..1 or any).
     */
    public static double lerpDamage(double min, double max, double power) {
        return min + (max - min) * clamp(power, 0.0, 1.0);
    }

    // ------------------------------------------------------------------
    // Damage application
    // ------------------------------------------------------------------

    /**
     * Deal raw damage to entity (triggers invulnerability / events).
     */
    public static void damage(LivingEntity target, double amount) {
        if (target == null || !target.isValid() || target.isDead() || amount <= 0) {
            return;
        }
        if (target instanceof Player player && isInvulnerable(player)) {
            return;
        }
        target.damage(amount);
    }

    /**
     * Deal damage attributed to an attacker.
     */
    public static void damage(LivingEntity target, double amount, LivingEntity attacker) {
        if (target == null || !target.isValid() || target.isDead() || amount <= 0) {
            return;
        }
        if (target instanceof Player player && isInvulnerable(player)) {
            return;
        }
        target.damage(amount, attacker);
    }

    /**
     * Deal skill damage with full pipeline then apply.
     *
     * @return final damage applied (0 if blocked)
     */
    public static double dealSkillDamage(
            LivingEntity attacker,
            LivingEntity target,
            double baseDamage,
            double scaling,
            double power,
            double criticalChance,
            double criticalMultiplier,
            double armorPenetration
    ) {
        double finalDamage = calculateFinalDamage(
                baseDamage, scaling, power, criticalChance, criticalMultiplier, armorPenetration, target);
        if (finalDamage <= 0) {
            return 0.0;
        }
        if (attacker != null) {
            damage(target, finalDamage, attacker);
        } else {
            damage(target, finalDamage);
        }
        return finalDamage;
    }

    /**
     * Deal damage ignoring armor entirely (true damage).
     */
    public static void dealTrueDamage(LivingEntity target, double amount, LivingEntity source) {
        if (target == null || amount <= 0 || !target.isValid() || target.isDead()) {
            return;
        }
        // bypass armor by dealing as much raw damage needed? better: use damage event cancel armor
        // Practical approach: deal damage through Bukkit which still applies armor,
        // so pre-scale amount to compensate.
        double compensated = compensateArmor(amount, target);
        if (source != null) {
            target.damage(compensated, source);
        } else {
            target.damage(compensated);
        }
    }

    /**
     * Scale damage up so after armor it equals the desired amount.
     */
    public static double compensateArmor(double desiredDamage, LivingEntity defender) {
        if (defender == null || desiredDamage <= 0) {
            return Math.max(0, desiredDamage);
        }
        double armor = getArmor(defender);
        double toughness = getArmorToughness(defender);
        // inverse of applyArmorFormula approx
        double reduction = armor - (desiredDamage / (toughness + ARMOR_CONSTANT));
        reduction = Math.min(20.0, Math.max(0.0, reduction));
        double multiplier = Math.max(1.0 - MAX_ARMOR_REDUCTION, 1.0 - (reduction / 25.0));
        return multiplier <= 0 ? desiredDamage : desiredDamage / multiplier;
    }

    // ------------------------------------------------------------------
    // Damage event helpers
    // ------------------------------------------------------------------

    /**
     * Read final damage from an event (after all modifiers).
     */
    public static double getEventFinalDamage(EntityDamageEvent event) {
        return event.getFinalDamage();
    }

    /**
     * Multiply damage in a damage event.
     */
    public static void multiplyEventDamage(EntityDamageEvent event, double multiplier) {
        event.setDamage(event.getDamage() * multiplier);
    }

    /**
     * Add flat damage in a damage event.
     */
    public static void addEventDamage(EntityDamageEvent event, double amount) {
        event.setDamage(event.getDamage() + amount);
    }

    /**
     * Get the damager if the event is caused by an entity.
     */
    public static LivingEntity getDamager(EntityDamageEvent event) {
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            return damager instanceof LivingEntity living ? living : null;
        }
        return null;
    }

    public static LivingEntity getVictim(EntityDamageEvent event) {
        return event.getEntity() instanceof LivingEntity living ? living : null;
    }

    // ------------------------------------------------------------------
    // Utility
    // ------------------------------------------------------------------

    public static boolean isInvulnerable(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isInvulnerable();
    }

    public static boolean isAlive(LivingEntity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public static boolean isHostile(LivingEntity entity) {
        return entity != null && !(entity instanceof Player);
    }

    public static boolean isPlayer(LivingEntity entity) {
        return entity instanceof Player;
    }

    /**
     * Effective damage after absorption hearts are considered.
     */
    public static double getEffectiveHealth(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    /**
     * Whether entity has absorption (totem / golden apple style).
     */
    public static boolean hasAbsorption(LivingEntity entity) {
        return entity.getAbsorptionAmount() > 0;
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Debug print for damage calculation (respects debug flag caller-side).
     */
    public static String describeCalculation(String label, double base, double finalDamage) {
        return String.format("[Damage] %s base=%.2f final=%.2f", label, base, finalDamage);
    }

    /**
     * Whether the entity is currently invulnerable from no-damage ticks.
     */
    public static boolean hasNoDamageTicks(LivingEntity entity) {
        return entity != null && entity.getNoDamageTicks() > 0;
    }

    /**
     * Force clear invulnerability frames (for multi-hit skills).
     */
    public static void resetNoDamageTicks(LivingEntity entity) {
        if (entity != null) {
            entity.setNoDamageTicks(0);
        }
    }

    /**
     * Check if target has a specific potion effect type by name (safer across versions).
     */
    public static boolean hasPotion(LivingEntity entity, PotionEffectType type) {
        return entity != null && type != null && entity.hasPotionEffect(type);
    }

    /**
     * Safety: run damage on main thread if needed.
     */
    public static void damageSync(LivingEntity target, double amount, LivingEntity attacker) {
        Runnable task = () -> {
            if (attacker != null) {
                damage(target, amount, attacker);
            } else {
                damage(target, amount);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(com.skillcore.SkillCorePlugin.getInstance(), task);
        }
    }
}
