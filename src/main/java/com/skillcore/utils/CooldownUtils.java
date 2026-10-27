package com.skillcore.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷却记录工具 — 按「玩家 + 冷却键」记录到期时间。
 * <p>
 * 冷却键由 {@code WeaponManager} 生成（如 {@code weapon:blade_dash:right}），
 * 因此同一把武器的左右键技能各自独立计时。
 */
public final class CooldownUtils {

    private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();

    private CooldownUtils() {
    }

    private static String key(UUID playerId, String skillId) {
        return playerId + ":" + skillId;
    }

    private static boolean invalid(UUID playerId, String skillId) {
        return playerId == null || skillId == null || skillId.isBlank();
    }

    /**
     * 开始冷却。
     *
     * @param seconds 冷却秒数（{@code <= 0} 或非有限值表示立即就绪）
     */
    public static void start(UUID playerId, String skillId, double seconds) {
        if (invalid(playerId, skillId)) {
            return;
        }
        if (!Double.isFinite(seconds) || seconds <= 0) {
            COOLDOWNS.remove(key(playerId, skillId));
            return;
        }
        // 上限 24 小时，避免配置写错（如 1e12）导致记录永不过期
        long millis = (long) Math.min(seconds * 1000.0, 86_400_000.0);
        COOLDOWNS.put(key(playerId, skillId), System.currentTimeMillis() + millis);
    }

    /** 该技能当前是否在冷却中（顺带清理已过期记录）。 */
    public static boolean isOnCooldown(UUID playerId, String skillId) {
        if (invalid(playerId, skillId)) {
            return false;
        }
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

    /** 剩余冷却秒数（0 = 已就绪）。 */
    public static double getRemaining(UUID playerId, String skillId) {
        if (invalid(playerId, skillId)) {
            return 0.0;
        }
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

    /** 剩余冷却的可读文本，如 {@code 3.2s} / {@code 1m 5s}。 */
    public static String getRemainingFormatted(UUID playerId, String skillId) {
        return TimeUtils.formatSeconds(getRemaining(playerId, skillId));
    }

    /** 强制清除某个冷却。 */
    public static void clear(UUID playerId, String skillId) {
        if (invalid(playerId, skillId)) {
            return;
        }
        COOLDOWNS.remove(key(playerId, skillId));
    }

    /** 清除该玩家的全部冷却。 */
    public static void clearAll(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String prefix = playerId + ":";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清空所有冷却记录（插件禁用时调用，避免热重载残留）。 */
    public static void clearAll() {
        COOLDOWNS.clear();
    }

    /**
     * 就绪则立即开始冷却并返回 true；冷却中返回 false 且不改变状态。
     */
    public static boolean tryUse(UUID playerId, String skillId, double seconds) {
        if (isOnCooldown(playerId, skillId)) {
            return false;
        }
        start(playerId, skillId, seconds);
        return true;
    }

    /** 是否就绪（不开始冷却）。 */
    public static boolean canUse(UUID playerId, String skillId) {
        return !isOnCooldown(playerId, skillId);
    }

    /** 清理已过期条目（定时调用，防止长期运行堆积）。 */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        COOLDOWNS.entrySet().removeIf(e -> e.getValue() <= now);
    }

    /** 当前记录数量（调试用）。 */
    public static int size() {
        return COOLDOWNS.size();
    }
}
