package com.skillcore.utils;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Damage reflection (反伤) toolkit.
 * <p>
 * Reflects a portion of incoming damage back to the attacker.
 * Supports flat + percent reflect, thorns-style, temporary reflect buffs,
 * and recursion protection.
 */
public final class ReflectDamageUtils {

    /** Prevents infinite reflect ping-pong. */
    private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);

    /** Temporary reflect state: uuid -> reflect data */
    private static final Map<UUID, ReflectBuff> BUFFS = new ConcurrentHashMap<>();

    private ReflectDamageUtils() {
    }

    /**
     * Immutable reflect parameters.
     */
    public record ReflectBuff(double percent, double flat, long expireAtMillis, boolean reflectToSelfOnly) {

        public boolean isExpired() {
            return expireAtMillis > 0 && System.currentTimeMillis() > expireAtMillis;
        }
    }

    // ------------------------------------------------------------------
    // Core calculation
    // ------------------------------------------------------------------

    /**
     * Calculate reflected damage.
     *
     * @param incomingDamage damage that hit the defender
     * @param percent        0.0 - 1.0+ (0.2 = reflect 20%)
     * @param flat           flat extra reflect
     */
    public static double calculateReflect(double incomingDamage, double percent, double flat) {
        if (incomingDamage <= 0) {
            return 0.0;
        }
        return incomingDamage * Math.max(0.0, percent) + Math.max(0.0, flat);
    }

    /**
     * Apply reflect: defender receives {@code incomingDamage}, attacker takes reflect.
     *
     * @return damage reflected (0 if none / recursion)
     */
    public static double reflect(LivingEntity defender, LivingEntity attacker, double incomingDamage, double percent, double flat) {
        if (defender == null || attacker == null || incomingDamage <= 0) {
            return 0.0;
        }
        if (REFLECTING.get()) {
            return 0.0; // recursion guard
        }
        if (!DamageUtils.isAlive(attacker) || attacker.equals(defender)) {
            return 0.0;
        }
        double reflectDamage = calculateReflect(incomingDamage, percent, flat);
        if (reflectDamage <= 0) {
            return 0.0;
        }
        // never fully one-shot through reflect alone: cap at attacker's current HP-1 if player
        if (attacker instanceof Player) {
            reflectDamage = Math.min(reflectDamage, Math.max(0.1, attacker.getHealth() - 0.5));
        }
        REFLECTING.set(true);
        try {
            DamageUtils.damage(attacker, reflectDamage, defender);
        } finally {
            REFLECTING.set(false);
        }
        return reflectDamage;
    }

    /**
     * Reflect from a damage event (does not modify original damage).
     */
    public static double reflectFromEvent(EntityDamageEvent event, LivingEntity defender, double percent, double flat) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            LivingEntity attacker = DamageUtils.getDamager(event);
            if (attacker != null) {
                return reflect(defender, attacker, event.getFinalDamage(), percent, flat);
            }
        }
        return 0.0;
    }

    // ------------------------------------------------------------------
    // Temporary reflect buffs
    // ------------------------------------------------------------------

    /**
     * Add / replace a timed reflect buff on an entity.
     *
     * @param durationTicks duration in ticks (20 = 1s)
     */
    public static void addReflectBuff(LivingEntity entity, double percent, double flat, long durationTicks) {
        if (entity == null) {
            return;
        }
        long expire = System.currentTimeMillis() + (durationTicks * 50L);
        BUFFS.put(entity.getUniqueId(), new ReflectBuff(percent, flat, expire, false));
    }

    public static void removeReflectBuff(LivingEntity entity) {
        if (entity != null) {
            BUFFS.remove(entity.getUniqueId());
        }
    }

    public static ReflectBuff getReflectBuff(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        ReflectBuff buff = BUFFS.get(entity.getUniqueId());
        if (buff == null) {
            return null;
        }
        if (buff.isExpired()) {
            BUFFS.remove(entity.getUniqueId());
            return null;
        }
        return buff;
    }

    /**
     * Apply reflect using active buff if present.
     *
     * @return reflected damage or 0
     */
    public static double reflectWithBuff(LivingEntity defender, LivingEntity attacker, double incomingDamage) {
        ReflectBuff buff = getReflectBuff(defender);
        if (buff == null) {
            return 0.0;
        }
        return reflect(defender, attacker, incomingDamage, buff.percent(), buff.flat());
    }

    /**
     * Thorns: always-on reflect, typically armor-based.
     */
    public static double thorns(LivingEntity defender, LivingEntity attacker, double incomingDamage, double thornsLevel) {
        // vanilla-ish: level * 0.15 flat-ish + small percent
        double percent = thornsLevel * 0.02;
        double flat = thornsLevel * 0.5;
        return reflect(defender, attacker, incomingDamage, percent, flat);
    }

    /**
     * Magic mirror: reflect 100% with a delay scheduler callback.
     * Caller schedules the actual reflect via the returned damage value.
     */
    public static double calculateFullReflect(double incomingDamage, double maxPercent) {
        return incomingDamage * Math.min(Math.max(0.0, maxPercent), 1.0);
    }

    public static void clearAllBuffs() {
        BUFFS.clear();
    }
}
