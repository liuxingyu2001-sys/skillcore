package com.skillcore.armor;

import org.bukkit.configuration.ConfigurationSection;

/**
 * 盔甲套装加成 — 集齐整套后生效的被动数值。
 * <p>
 * 字段全部有默认值，{@code armor/<id>.yml} 只写需要覆盖的键。
 * 目前只包含被动数值（吸血 / 反伤 / 增伤 / 减伤），
 * 后续盔甲技能通过 {@link ArmorSkill} 扩展主动能力。
 */
public final class ArmorStats {

    /** 空加成（未穿戴整套时返回），全部为默认值。 */
    public static final ArmorStats EMPTY = new ArmorStats();

    // ---- 吸血 / 反伤 ----
    private double lifesteal = 0.0;
    private double reflectPercent = 0.0;
    private double reflectFlat = 0.0;

    // ---- 伤害 ----
    /** 造成伤害倍率（1.0 = 不变）。 */
    private double damageMultiplier = 1.0;
    /** 受到伤害减免（0.0~1.0，0.1 = 减免 10%）。 */
    private double damageReduction = 0.0;

    // ---- 属性（穿戴整套时加成，脱下自动移除） ----
    /** 最大生命值加成（ADD_NUMBER）。 */
    private double maxHealthBonus = 0.0;
    /** 移动速度加成（MULTIPLY_SCALAR_1，0.1 = +10%）。 */
    private double movementSpeedPercent = 0.0;

    // ---- 自定义扩展（盔甲技能专用键，如 sunfire-*） ----
    private final java.util.Map<String, Object> custom = new java.util.concurrent.ConcurrentHashMap<>();

    public ArmorStats() {
    }

    public double lifesteal() {
        return lifesteal;
    }

    public double reflectPercent() {
        return reflectPercent;
    }

    public double reflectFlat() {
        return reflectFlat;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public double damageReduction() {
        return damageReduction;
    }

    public double maxHealthBonus() {
        return maxHealthBonus;
    }

    public double movementSpeedPercent() {
        return movementSpeedPercent;
    }

    public double customDouble(String key, double def) {
        Object v = custom.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public int customInt(String key, int def) {
        Object v = custom.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public String customString(String key, String def) {
        Object v = custom.get(key);
        return v instanceof String s ? s : def;
    }

    public boolean customBoolean(String key, boolean def) {
        Object v = custom.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public static ArmorStats fromConfig(ConfigurationSection section) {
        ArmorStats s = new ArmorStats();
        if (section == null) {
            return s;
        }
        s.lifesteal = section.getDouble("lifesteal", s.lifesteal);
        s.reflectPercent = section.getDouble("reflect-percent", section.getDouble("reflect", s.reflectPercent));
        s.reflectFlat = section.getDouble("reflect-flat", s.reflectFlat);
        s.damageMultiplier = section.getDouble("damage-multiplier", section.getDouble("damage-boost", s.damageMultiplier));
        s.damageReduction = section.getDouble("damage-reduction", section.getDouble("dr", s.damageReduction));
        s.maxHealthBonus = section.getDouble("max-health", s.maxHealthBonus);
        s.movementSpeedPercent = section.getDouble("movement-speed-percent", s.movementSpeedPercent);
        // 全部键都进 custom，供盔甲技能读取任意自定义键（如 sunfire-*）
        for (String key : section.getKeys(false)) {
            s.custom.put(key, section.get(key));
        }
        return s;
    }
}
