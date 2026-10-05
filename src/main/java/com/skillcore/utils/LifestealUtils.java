package com.skillcore.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent;

/**
 * Lifesteal (吸血) toolkit.
 * <p>
 * Converts a portion of dealt damage into healing for the attacker.
 * Supports flat lifesteal, percent of damage dealt, percent of max HP,
 * overheal into absorption, and caps.
 */
public final class LifestealUtils {

    private LifestealUtils() {
    }

    /**
     * Heal attacker for a percentage of damage dealt.
     *
     * @param damageDealt damage that was actually applied
     * @param percent     0.0 - 1.0 (0.15 = 15% lifesteal)
     * @return amount healed (0 if invalid)
     */
    public static double healByDamagePercent(LivingEntity attacker, double damageDealt, double percent) {
        if (attacker == null || damageDealt <= 0 || percent <= 0) {
            return 0.0;
        }
        return heal(attacker, damageDealt * percent);
    }

    /**
     * Flat heal (absolute HP).
     */
    public static double heal(LivingEntity entity, double amount) {
        if (entity == null || amount <= 0 || !DamageUtils.isAlive(entity)) {
            return 0.0;
        }
        double max = DamageUtils.getMaxHealth(entity);
        double before = entity.getHealth();
        double after = Math.min(max, before + amount);
        double healed = after - before;
        if (healed > 0) {
            entity.heal(healed, EntityRegainHealthEvent.RegainReason.CUSTOM);
        }
        return healed;
    }

    /**
     * Heal that can overflow into absorption hearts (overheal).
     *
     * @param maxAbsorption hard cap on absorption amount
     * @return total effective heal (hp + absorption gained)
     */
    public static double healWithOverheal(LivingEntity entity, double amount, double maxAbsorption) {
        if (entity == null || amount <= 0 || !DamageUtils.isAlive(entity)) {
            return 0.0;
        }
        double healed = heal(entity, amount);
        double leftover = amount - healed;
        if (leftover > 0) {
            double currentAbs = entity.getAbsorptionAmount();
            double absGain = Math.min(maxAbsorption - currentAbs, leftover);
            if (absGain > 0) {
                entity.setAbsorptionAmount(currentAbs + absGain);
                healed += absGain;
            }
        }
        return healed;
    }

    /**
     * Heal a percentage of the attacker's max health.
     */
    public static double healByMaxHealthPercent(LivingEntity attacker, double percent) {
        if (attacker == null || percent <= 0) {
            return 0.0;
        }
        return heal(attacker, DamageUtils.getMaxHealth(attacker) * percent);
    }

    /**
     * Heal a percentage of the target's max health (vampiric style).
     */
    public static double healByTargetMaxHealth(LivingEntity attacker, LivingEntity target, double percent) {
        if (attacker == null || target == null || percent <= 0) {
            return 0.0;
        }
        return heal(attacker, DamageUtils.getMaxHealth(target) * percent);
    }

    /**
     * Full lifesteal application with optional cap and overheal.
     *
     * @param damageDealt  damage actually dealt
     * @param percent      lifesteal ratio (0.0 - 1.0+)
     * @param maxHeal      maximum heal per hit, {@code <= 0} for uncapped
     * @param allowOverheal whether leftover becomes absorption
     * @param maxAbsorption absorption cap when overheal enabled
     * @return amount actually gained (hp + absorption)
     */
    public static double applyLifesteal(
            LivingEntity attacker,
            double damageDealt,
            double percent,
            double maxHeal,
            boolean allowOverheal,
            double maxAbsorption
    ) {
        if (attacker == null || damageDealt <= 0 || percent <= 0) {
            return 0.0;
        }
        double amount = damageDealt * percent;
        if (maxHeal > 0) {
            amount = Math.min(amount, maxHeal);
        }
        if (allowOverheal) {
            return healWithOverheal(attacker, amount, maxAbsorption);
        }
        return heal(attacker, amount);
    }

    /**
     * Simple default: 10% lifesteal, no overheal.
     */
    public static void lifestealDefault(LivingEntity attacker, double damageDealt) {
        healByDamagePercent(attacker, damageDealt, 0.10);
    }

    /**
     * Whether entity is at full health.
     */
    public static boolean isFullHealth(LivingEntity entity) {
        return entity != null && entity.getHealth() >= DamageUtils.getMaxHealth(entity) - 0.001;
    }

    /**
     * Remaining heal potential before full.
     */
    public static double getMissingHealth(LivingEntity entity) {
        return DamageUtils.getMissingHealth(entity);
    }
}
