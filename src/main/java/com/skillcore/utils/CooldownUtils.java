package com.skillcore.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldown tracking toolkit (per-player / per-skill).
 */
public final class CooldownUtils {

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private CooldownUtils() {
    }

    private static String key(UUID player, String skillId) {
        return player + ":" + skillId;
    }

    /**
     * Start a cooldown for a skill.
     *
     * @param seconds cooldown duration
     */
    public static void start(UUID playerId, String skillId, double seconds) {
        if (playerId == null || skillId == null) {
            return;
        }
        if (seconds <= 0) {
            COOLDOWNS.remove(key(playerId, skillId));
            return;
        }
        COOLDOWNS.put(key(playerId, skillId), System.currentTimeMillis() + (long) (seconds * 1000L));
    }

    /**
     * Whether the skill is currently on cooldown.
     */
    public static boolean isOnCooldown(UUID playerId, String skillId) {
        Long until = COOLDOWNS.get(key(playerId, skillId));
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            COOLDOWNS.remove(key(playerId, skillId));
            return false;
        }
        return true;
    }

    /**
     * Remaining seconds (0 if ready).
     */
    public static double getRemaining(UUID playerId, String skillId) {
        Long until = COOLDOWNS.get(key(playerId, skillId));
        if (until == null) {
            return 0.0;
        }
        long diff = until - System.currentTimeMillis();
        if (diff <= 0) {
            COOLDOWNS.remove(key(playerId, skillId));
            return 0.0;
        }
        return diff / 1000.0;
    }

    /**
     * Remaining formatted string e.g. "3.2s" / "1m 5s".
     */
    public static String getRemainingFormatted(UUID playerId, String skillId) {
        return TimeUtils.formatSeconds(getRemaining(playerId, skillId));
    }

    /**
     * Force clear a cooldown.
     */
    public static void clear(UUID playerId, String skillId) {
        COOLDOWNS.remove(key(playerId, skillId));
    }

    /**
     * Clear all cooldowns of a player.
     */
    public static void clearAll(UUID playerId) {
        String prefix = playerId + ":";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Whether ready; if not ready, does nothing. If ready, starts new cooldown.
     *
     * @return true if the skill can be used now (and cooldown was started)
     */
    public static boolean tryUse(UUID playerId, String skillId, double seconds) {
        if (isOnCooldown(playerId, skillId)) {
            return false;
        }
        start(playerId, skillId, seconds);
        return true;
    }

    /**
     * Whether ready without starting cooldown.
     */
    public static boolean canUse(UUID playerId, String skillId) {
        return !isOnCooldown(playerId, skillId);
    }

    /**
     * Cleanup expired entries (call periodically).
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
