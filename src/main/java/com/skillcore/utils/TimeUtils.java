package com.skillcore.utils;

import java.util.Locale;

/**
 * 时间 / 时长格式化工具。
 * <p>
 * 所有格式化固定 {@link Locale#ROOT}，避免某些语言环境把小数点输出成逗号。
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    public static long secondsToMillis(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return 0L;
        }
        return (long) Math.min(seconds * 1000.0, 86_400_000.0);
    }

    public static double millisToSeconds(long millis) {
        return millis / 1000.0;
    }

    public static long secondsToTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return 0L;
        }
        return (long) Math.min(seconds * 20.0, 1_728_000.0);
    }

    public static double ticksToSeconds(long ticks) {
        return ticks / 20.0;
    }

    /**
     * 格式化为 {@code 3.2s} / {@code 45s} / {@code 1m 5s} / {@code 2h 3m}。
     * <p>
     * 负数与非有限值统一显示 {@code 0s}。
     */
    public static String formatSeconds(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            return "0s";
        }
        if (seconds < 10) {
            return String.format(Locale.ROOT, "%.1fs", seconds);
        }
        if (seconds < 60) {
            return String.format(Locale.ROOT, "%.0fs", seconds);
        }
        if (seconds < 3600) {
            long m = (long) (seconds / 60);
            long s = (long) (seconds % 60);
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        long h = (long) (seconds / 3600);
        long m = (long) ((seconds % 3600) / 60);
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    /** 毫秒格式化为 {@code 3.2s}。 */
    public static String formatMillis(long millis) {
        return formatSeconds(millis / 1000.0);
    }

    /** 时间戳（毫秒）是否已过期。 */
    public static boolean isExpired(long expireAtMillis) {
        return System.currentTimeMillis() >= expireAtMillis;
    }

    /** 距离时间戳还剩多少毫秒（已过期返回 0）。 */
    public static long remainingMillis(long expireAtMillis) {
        return Math.max(0, expireAtMillis - System.currentTimeMillis());
    }

    /** 距离时间戳还剩多少秒。 */
    public static double remainingSeconds(long expireAtMillis) {
        return remainingMillis(expireAtMillis) / 1000.0;
    }
}
