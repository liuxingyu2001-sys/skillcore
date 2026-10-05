package com.skillcore.utils;

import org.bukkit.entity.LivingEntity;

/**
 * Percentage-based damage (百分比伤害) toolkit.
 * <p>
 * Common patterns: max-HP%, current-HP%, missing-HP% (execute),
 * with optional caps and combination formulas.
 */
public final class PercentageDamageUtils {

    private PercentageDamageUtils() {
    }

    /**
     * Damage equal to a percent of target's MAX health.
     *
     * @param percent 0.08 = 8% max HP
     */
    public static double ofMaxHealth(LivingEntity target, double percent) {
        if (target == null || percent <= 0) {
            return 0.0;
        }
        return DamageUtils.getMaxHealth(target) * percent;
    }

    /**
     * Damage equal to a percent of target's CURRENT health.
     */
    public static double ofCurrentHealth(LivingEntity target, double percent) {
        if (target == null || percent <= 0) {
            return 0.0;
        }
        return target.getHealth() * percent;
    }

    /**
     * Damage equal to a percent of target's MISSING health (execute / finisher).
     */
    public static double ofMissingHealth(LivingEntity target, double percent) {
        if (target == null || percent <= 0) {
            return 0.0;
        }
        return DamageUtils.getMissingHealth(target) * percent;
    }

    /**
     * Capped percent-of-max damage (prevent one-shot bosses).
     *
     * @param maxDamage hard cap, {@code <= 0} = uncapped
     */
    public static double ofMaxHealthCapped(LivingEntity target, double percent, double maxDamage) {
        double damage = ofMaxHealth(target, percent);
        return maxDamage > 0 ? Math.min(damage, maxDamage) : damage;
    }

    /**
     * Capped percent-of-current damage.
     */
    public static double ofCurrentHealthCapped(LivingEntity target, double percent, double maxDamage) {
        double damage = ofCurrentHealth(target, percent);
        return maxDamage > 0 ? Math.min(damage, maxDamage) : damage;
    }

    /**
     * Combined formula: {@code flat + percentMax * maxHp + percentMissing * missingHp}.
     */
    public static double combine(
            LivingEntity target,
            double flat,
            double percentMax,
            double percentMissing
    ) {
        if (target == null) {
            return Math.max(0.0, flat);
        }
        return flat
                + ofMaxHealth(target, percentMax)
                + ofMissingHealth(target, percentMissing);
    }

    /**
     * Execute damage: if target is below threshold, deal percent of missing HP.
     *
     * @param healthThreshold 0.0 - 1.0 (0.25 = below 25% HP)
     * @param executePercent  0.0 - 1.0 of missing health
     * @return damage if execute applies, otherwise 0
     */
    public static double executeIfBelow(LivingEntity target, double healthThreshold, double executePercent) {
        if (target == null) {
            return 0.0;
        }
        if (DamageUtils.getHealthPercent(target) > healthThreshold) {
            return 0.0;
        }
        return ofMissingHealth(target, executePercent);
    }

    /**
     * True if target is below a health threshold.
     */
    public static boolean isBelowThreshold(LivingEntity target, double healthThreshold) {
        return target != null && DamageUtils.getHealthPercent(target) <= healthThreshold;
    }

    /**
     * Apply percent-of-max damage through the damage system (triggers events).
     */
    public static void dealPercentOfMaxHealth(LivingEntity attacker, LivingEntity target, double percent) {
        dealPercentOfMaxHealth(attacker, target, percent, 0);
    }

    public static void dealPercentOfMaxHealth(LivingEntity attacker, LivingEntity target, double percent, double maxDamage) {
        double damage = ofMaxHealthCapped(target, percent, maxDamage);
        if (damage > 0) {
            DamageUtils.damage(target, damage, attacker);
        }
    }

    public static void dealPercentOfCurrentHealth(LivingEntity attacker, LivingEntity target, double percent, double maxDamage) {
        double damage = ofCurrentHealthCapped(target, percent, maxDamage);
        if (damage > 0) {
            DamageUtils.damage(target, damage, attacker);
        }
    }

    public static void dealPercentOfMissingHealth(LivingEntity attacker, LivingEntity target, double percent, double maxDamage) {
        double damage = ofMissingHealth(target, percent);
        if (maxDamage > 0) {
            damage = Math.min(damage, maxDamage);
        }
        if (damage > 0) {
            DamageUtils.damage(target, damage, attacker);
        }
    }

    /**
     * Soft damage that reduces health without killing (min HP floor).
     */
    public static double dealPercentNonLethal(LivingEntity target, double percent, double minHealthLeft) {
        if (target == null || percent <= 0) {
            return 0.0;
        }
        double damage = ofCurrentHealth(target, percent);
        double maxDamage = target.getHealth() - Math.max(0.0, minHealthLeft);
        damage = Math.min(damage, Math.max(0.0, maxDamage));
        if (damage > 0) {
            target.setHealth(Math.max(minHealthLeft, target.getHealth() - damage));
        }
        return damage;
    }
}
