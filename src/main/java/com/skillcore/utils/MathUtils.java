package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Math helpers for skill design (scaling, random, interpolation, angles).
 */
public final class MathUtils {

    private MathUtils() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp(t, 0.0, 1.0);
    }

    public static double inverseLerp(double a, double b, double value) {
        if (Math.abs(b - a) < 1.0e-9) {
            return 0.0;
        }
        return clamp((value - a) / (b - a), 0.0, 1.0);
    }

    /**
     * Smoothstep interpolation.
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        double t = inverseLerp(edge0, edge1, x);
        return t * t * (3 - 2 * t);
    }

    /**
     * Random double in [min, max).
     */
    public static double randomRange(double min, double max) {
        return min + Math.random() * (max - min);
    }

    /**
     * Random int in [min, max] inclusive.
     */
    public static int randomInt(int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return min + (int) (Math.random() * (max - min + 1));
    }

    /**
     * Random chance check (0.0 - 1.0).
     */
    public static boolean chance(double probability) {
        return Math.random() < clamp(probability, 0.0, 1.0);
    }

    /**
     * Percent chance (0-100).
     */
    public static boolean chancePercent(double percent) {
        return chance(percent / 100.0);
    }

    /**
     * Scale damage with diminishing returns: base * (1 + factor * level / (1 + level)).
     */
    public static double diminishingScaling(double base, double factor, double level) {
        return base * (1.0 + factor * level / (1.0 + level));
    }

    /**
     * Exponential scaling: base * pow(multiplier, level).
     */
    public static double exponentialScaling(double base, double multiplier, double level) {
        return base * Math.pow(multiplier, level);
    }

    /**
     * Soft cap: value that approaches cap asymptotically.
     */
    public static double softCap(double value, double cap, double steepness) {
        if (value <= cap) {
            return value;
        }
        double excess = value - cap;
        return cap + cap * steepness * excess / (cap + steepness * excess);
    }

    /**
     * Linear interpolation between two locations (ignoring yaw/pitch).
     */
    public static Location lerpLocation(Location a, Location b, double t) {
        if (a == null || b == null) {
            return a != null ? a.clone() : b;
        }
        double x = lerp(a.getX(), b.getX(), t);
        double y = lerp(a.getY(), b.getY(), t);
        double z = lerp(a.getZ(), b.getZ(), t);
        Location result = a.clone();
        result.setX(x);
        result.setY(y);
        result.setZ(z);
        return result;
    }

    /**
     * Angle in degrees between two direction vectors.
     */
    public static double angleDegrees(Vector a, Vector b) {
        if (a == null || b == null) {
            return 0;
        }
        double dot = a.normalize().dot(b.normalize());
        return Math.toDegrees(Math.acos(clamp(dot, -1.0, 1.0)));
    }

    /**
     * Angle between entity look and direction to a point.
     */
    public static double lookAngleDegrees(Location eye, Vector lookDirection, Location target) {
        if (eye == null || lookDirection == null || target == null) {
            return 180;
        }
        Vector toTarget = target.toVector().subtract(eye.toVector());
        return angleDegrees(lookDirection, toTarget);
    }

    /**
     * Round to decimals.
     */
    public static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    /**
     * Format a double as 1 decimal string for UI.
     */
    public static String format1(double value) {
        return String.format("%.1f", value);
    }

    public static String format2(double value) {
        return String.format("%.2f", value);
    }

    /**
     * Convert a "3.5" style level to damage contribution.
     */
    public static double levelFactor(double level, double coefficient) {
        return level * coefficient;
    }

    /**
     * Percentage as 0-1 from "15" style.
     */
    public static double percentToRatio(double percent) {
        return percent / 100.0;
    }

    /**
     * Ratio as 0-100 from 0.15 style.
     */
    public static double ratioToPercent(double ratio) {
        return ratio * 100.0;
    }

    /**
     * Whether two doubles are nearly equal.
     */
    public static boolean nearlyEqual(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    /**
     * Fibonacci-ish spiral offset for multi-projectile spread.
     */
    public static Vector spreadOffset(int index, int total, double radius) {
        if (total <= 0) {
            return new Vector();
        }
        double angle = 2 * Math.PI * index / total;
        return new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    /**
     * Biased random: higher chance for values near max.
     */
    public static double randomBiasedHigh(double min, double max) {
        double t = 1.0 - Math.random(); // bias toward 1
        return lerp(min, max, t * t);
    }
}
