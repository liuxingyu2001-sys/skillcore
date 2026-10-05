package com.skillcore.effect;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 特效全局设置 — 由 config.yml 的 {@code effects:} 段加载。
 * <p>
 * 所有特效绘制都应先检查 {@link #effectsEnabled()} / {@link #particlesEnabled()}，
 * 便于服务器统一关闭特效降负载。
 */
public final class EffectSettings {

    private static volatile boolean effectsEnabled = true;
    private static volatile boolean particlesEnabled = true;
    private static volatile double density = 1.0;
    private static volatile String fallbackParticle = "ENCHANT";
    private static volatile int teleportDuration = 3;
    private static volatile int interpolationDuration = 2;

    private EffectSettings() {
    }

    public static void load(FileConfiguration config) {
        if (config == null) return;
        effectsEnabled = config.getBoolean("effects.enabled", true);
        particlesEnabled = config.getBoolean("effects.particles", true);
        density = Math.max(0.0, config.getDouble("effects.density", 1.0));
        fallbackParticle = config.getString("effects.fallback-particle", "ENCHANT");
        teleportDuration = clamp(config.getInt("effects.display.teleport-duration", 3), 0, 59);
        interpolationDuration = clamp(config.getInt("effects.display.interpolation-duration", 2), 0, 59);
    }

    public static boolean effectsEnabled() {
        return effectsEnabled;
    }

    public static boolean particlesEnabled() {
        return particlesEnabled && effectsEnabled;
    }

    /** 粒子密度倍率（0 = 不画，1 = 正常）。 */
    public static double density() {
        return density;
    }

    public static String fallbackParticle() {
        return fallbackParticle;
    }

    public static int teleportDuration() {
        return teleportDuration;
    }

    public static int interpolationDuration() {
        return interpolationDuration;
    }

    /** 按密度缩放粒子数量（至少 1，density<=0 返回 0）。 */
    public static int scaleCount(int count) {
        if (density <= 0) return 0;
        return Math.max(1, (int) Math.round(count * density));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
