package com.skillcore.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Potion / status effect toolkit.
 */
public final class PotionUtils {

    private PotionUtils() {
    }

    /**
     * Apply a potion effect. If an equal or stronger effect exists, replace it.
     */
    public static boolean apply(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier) {
        return apply(entity, type, durationTicks, amplifier, true, true);
    }

    public static boolean apply(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier,
                                boolean ambient, boolean particles) {
        if (entity == null || type == null || !DamageUtils.isAlive(entity)) {
            return false;
        }
        PotionEffect effect = new PotionEffect(type, durationTicks, amplifier, ambient, particles, true);
        return entity.addPotionEffect(effect);
    }

    /**
     * Apply only if target doesn't already have it (no refresh / no override).
     */
    public static boolean applyIfAbsent(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier) {
        if (entity == null || type == null || entity.hasPotionEffect(type)) {
            return false;
        }
        return apply(entity, type, durationTicks, amplifier);
    }

    /**
     * Refresh / extend an existing effect or apply new.
     */
    public static boolean refresh(LivingEntity entity, PotionEffectType type, int durationTicks, int amplifier) {
        return apply(entity, type, durationTicks, amplifier);
    }

    public static void remove(LivingEntity entity, PotionEffectType type) {
        if (entity != null && type != null) {
            entity.removePotionEffect(type);
        }
    }

    public static boolean has(LivingEntity entity, PotionEffectType type) {
        return entity != null && type != null && entity.hasPotionEffect(type);
    }

    public static int getAmplifier(LivingEntity entity, PotionEffectType type) {
        if (entity == null || type == null) {
            return -1;
        }
        PotionEffect effect = entity.getPotionEffect(type);
        return effect == null ? -1 : effect.getAmplifier();
    }

    public static int getRemainingTicks(LivingEntity entity, PotionEffectType type) {
        if (entity == null || type == null) {
            return 0;
        }
        PotionEffect effect = entity.getPotionEffect(type);
        return effect == null ? 0 : effect.getDuration();
    }

    /**
     * Clear all negative effects (dispel).
     */
    public static void dispelDebuffs(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            if (effect.getType() != null && isNegative(effect.getType())) {
                entity.removePotionEffect(effect.getType());
            }
        }
    }

    /**
     * Clear all positive effects (purge buffs).
     */
    public static void purgeBuffs(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            if (effect.getType() != null && !isNegative(effect.getType())) {
                entity.removePotionEffect(effect.getType());
            }
        }
    }

    /**
     * Whether the potion type is typically a debuff.
     */
    public static boolean isNegative(PotionEffectType type) {
        if (type == null) {
            return false;
        }
        // Compare against known debuff types
        return type == PotionEffectType.WITHER
                || type == PotionEffectType.POISON
                || type == PotionEffectType.WEAKNESS
                || type == PotionEffectType.SLOWNESS
                || type == PotionEffectType.MINING_FATIGUE
                || type == PotionEffectType.BLINDNESS
                || type == PotionEffectType.HUNGER
                || type == PotionEffectType.NAUSEA
                || type == PotionEffectType.LEVITATION
                || type == PotionEffectType.UNLUCK
                || type == PotionEffectType.DARKNESS
                || type == PotionEffectType.INFESTED
                || type == PotionEffectType.RAID_OMEN
                || type == PotionEffectType.WIND_CHARGED
                || type == PotionEffectType.WEAVING
                || type == PotionEffectType.OOZING;
    }

    /**
     * Buff shortcuts.
     */
    public static void strength(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.STRENGTH, durationTicks, amplifier);
    }

    public static void speed(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.SPEED, durationTicks, amplifier);
    }

    public static void resistance(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.RESISTANCE, durationTicks, amplifier);
    }

    public static void regeneration(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.REGENERATION, durationTicks, amplifier);
    }

    public static void absorption(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.ABSORPTION, durationTicks, amplifier);
    }

    public static void fireResistance(LivingEntity entity, int durationTicks) {
        apply(entity, PotionEffectType.FIRE_RESISTANCE, durationTicks, 0);
    }

    public static void jumpBoost(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.JUMP_BOOST, durationTicks, amplifier);
    }

    /**
     * Debuff shortcuts.
     */
    public static void poison(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.POISON, durationTicks, amplifier);
    }

    public static void wither(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.WITHER, durationTicks, amplifier);
    }

    public static void slowness(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.SLOWNESS, durationTicks, amplifier);
    }

    public static void weakness(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.WEAKNESS, durationTicks, amplifier);
    }

    public static void blindness(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.BLINDNESS, durationTicks, amplifier);
    }

    public static void levitation(LivingEntity entity, int durationTicks, int amplifier) {
        apply(entity, PotionEffectType.LEVITATION, durationTicks, amplifier);
    }

    public static void glowing(LivingEntity entity, int durationTicks) {
        apply(entity, PotionEffectType.GLOWING, durationTicks, 0);
    }

    public static void slowFalling(LivingEntity entity, int durationTicks) {
        apply(entity, PotionEffectType.SLOW_FALLING, durationTicks, 0);
    }

    /**
     * Stun-like combo: weakness + slowness.
     */
    public static void stun(LivingEntity entity, int durationTicks) {
        weakness(entity, durationTicks, 1);
        slowness(entity, durationTicks, 3);
    }

    /**
     * Root (cannot move): high slowness.
     */
    public static void root(LivingEntity entity, int durationTicks) {
        slowness(entity, durationTicks, 250);
    }
}
