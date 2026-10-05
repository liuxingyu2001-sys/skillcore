package com.skillcore.utils;

/**
 * Time / duration formatting utilities.
 */
public final class TimeUtils {

    private TimeUtils() {
    }

    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    public static long secondsToMillis(double seconds) {
        return (long) (seconds * 1000L);
    }

    public static double millisToSeconds(long millis) {
        return millis / 1000.0;
    }

    public static long secondsToTicks(double seconds) {
        return (long) (seconds * 20L);
    }

    public static double ticksToSeconds(long ticks) {
        return ticks / 20.0;
    }

    /**
     * Format seconds as "3.2s" / "45s" / "1m 5s" / "2h 3m".
     */
    public static String formatSeconds(double seconds) {
        if (seconds < 0) {
            return "0s";
        }
        if (seconds < 10) {
            return String.format("%.1fs", seconds);
        }
        if (seconds < 60) {
            return String.format("%.0fs", seconds);
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

    /**
     * Format millis as "3.2s".
     */
    public static String formatMillis(long millis) {
        return formatSeconds(millis / 1000.0);
    }

    /**
     * Whether a timestamp (millis) has expired.
     */
    public static boolean isExpired(long expireAtMillis) {
        return System.currentTimeMillis() >= expireAtMillis;
    }

    /**
     * Remaining millis until timestamp (0 if expired).
     */
    public static long remainingMillis(long expireAtMillis) {
        return Math.max(0, expireAtMillis - System.currentTimeMillis());
    }

    /**
     * Remaining seconds until timestamp.
     */
    public static double remainingSeconds(long expireAtMillis) {
        return remainingMillis(expireAtMillis) / 1000.0;
    }
}
