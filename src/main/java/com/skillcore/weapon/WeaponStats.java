package com.skillcore.weapon;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;

/**
 * 技能武器数值 — 每把武器独立配置，写技能时通过 {@link WeaponContext#stats()} 读取。
 * <p>
 * 全部字段都有默认值，配置里只写需要覆盖的部分即可。
 */
public final class WeaponStats {

    // ---- 基础 ----
    private double damage = 5.0;
    private double damageScaling = 0.0;
    private double criticalChance = 0.1;
    private double criticalMultiplier = 1.5;
    private double armorPenetration = 0.0;
    private double attackSpeed = 1.0;

    // ---- 百分比伤害 ----
    private double percentMaxHealth = 0.0;
    private double percentCurrentHealth = 0.0;
    private double percentMissingHealth = 0.0;
    private double percentDamageCap = 0.0;

    // ---- 吸血 / 反伤 ----
    private double lifesteal = 0.0;
    private double lifestealOverhealCap = 0.0;
    private double reflectPercent = 0.0;
    private double reflectFlat = 0.0;
    /** 反伤 buff 持续时长（tick，右键开启反伤类技能用；0 = 无持续状态）。 */
    private int reflectDurationTicks = 200;

    // ---- 冷却 / 消耗 ----
    private double cooldown = 1.0;
    /** 左键技能冷却（-1 = 未设置，回退 cooldown）。 */
    private double cooldownLeft = -1.0;
    /** 右键技能冷却（-1 = 未设置，回退 cooldown）。 */
    private double cooldownRight = -1.0;
    private double manaCost = 0.0;
    private double staminaCost = 0.0;
    /** 命中附带效果间隔（秒，0 = 每次命中都触发）。 */
    private double onHitIntervalSeconds = 0.5;

    // ---- 位移 ----
    private double dashSpeed = 1.2;
    private double dashDistance = 4.0;
    private double dashY = 0.15;
    private double blinkDistance = 6.0;
    private double knockbackStrength = 0.4;
    private double pullStrength = 0.5;

    // ---- 跳跃 / 砸地位移（风暴战锤等） ----
    private double hopVelocity = 0.55;
    private double hopForward = 0.12;
    private double slamLaunchVelocity = 0.9;
    private int slamApexTicks = 10;
    private double slamDownVelocity = -1.6;
    private int slamTimeoutTicks = 80;
    private double slamUppercutVelocity = 0.9;
    private double slamSelfBounce = 0.35;

    // ---- 瞄准 / 范围 ----
    private double range = 6.0;
    private double aimRange = 12.0;
    private double aimAngle = 35.0;
    private double aoeRadius = 3.0;
    private double aoeDamageRatio = 0.5;

    // ---- 控制 ----
    private int slowDurationTicks = 40;
    private int slowAmplifier = 1;
    private int stunDurationTicks = 20;
    private int rootDurationTicks = 30;

    // ---- 防御 ----
    private double damageReduction = 0.0;
    private double shieldAmount = 0.0;
    private double healAmount = 0.0;
    private double healPercentOfMax = 0.0;

    // ---- 多段 / 连击 ----
    private int hitCount = 1;
    private double hitIntervalTicks = 4;
    private int maxTargets = 1;

    // ---- 特效 ----
    private String particle = "CRIT";
    private String sound = "ENTITY_PLAYER_ATTACK_STRONG";
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    // ---- 自定义扩展 ----
    private final java.util.Map<String, Object> custom = new java.util.concurrent.ConcurrentHashMap<>();

    public WeaponStats() {
    }

    // ------------------------------------------------------------------
    // Builder 风格设置（链式）
    // ------------------------------------------------------------------

    public WeaponStats damage(double v) { this.damage = v; return this; }
    public WeaponStats damageScaling(double v) { this.damageScaling = v; return this; }
    public WeaponStats critical(double chance, double multiplier) {
        this.criticalChance = chance; this.criticalMultiplier = multiplier; return this;
    }
    public WeaponStats armorPenetration(double v) { this.armorPenetration = v; return this; }
    public WeaponStats attackSpeed(double v) { this.attackSpeed = v; return this; }

    public WeaponStats percentMaxHealth(double v) { this.percentMaxHealth = v; return this; }
    public WeaponStats percentCurrentHealth(double v) { this.percentCurrentHealth = v; return this; }
    public WeaponStats percentMissingHealth(double v) { this.percentMissingHealth = v; return this; }
    public WeaponStats percentDamageCap(double v) { this.percentDamageCap = v; return this; }

    public WeaponStats lifesteal(double v) { this.lifesteal = v; return this; }
    public WeaponStats reflect(double percent, double flat) {
        this.reflectPercent = percent; this.reflectFlat = flat; return this;
    }
    public WeaponStats reflectDuration(int ticks) { this.reflectDurationTicks = ticks; return this; }

    public WeaponStats cooldown(double v) { this.cooldown = v; return this; }
    public WeaponStats cooldownLeft(double v) { this.cooldownLeft = v; return this; }
    public WeaponStats cooldownRight(double v) { this.cooldownRight = v; return this; }
    public WeaponStats onHitInterval(double v) { this.onHitIntervalSeconds = v; return this; }
    public WeaponStats manaCost(double v) { this.manaCost = v; return this; }

    public WeaponStats dash(double speed, double distance, double y) {
        this.dashSpeed = speed; this.dashDistance = distance; this.dashY = y; return this;
    }
    public WeaponStats blinkDistance(double v) { this.blinkDistance = v; return this; }
    public WeaponStats knockback(double v) { this.knockbackStrength = v; return this; }
    public WeaponStats pullStrength(double v) { this.pullStrength = v; return this; }

    public WeaponStats range(double v) { this.range = v; return this; }
    public WeaponStats aim(double range, double angle) {
        this.aimRange = range; this.aimAngle = angle; return this;
    }
    public WeaponStats aoe(double radius, double damageRatio) {
        this.aoeRadius = radius; this.aoeDamageRatio = damageRatio; return this;
    }

    public WeaponStats slow(int ticks, int amplifier) {
        this.slowDurationTicks = ticks; this.slowAmplifier = amplifier; return this;
    }
    public WeaponStats stun(int ticks) { this.stunDurationTicks = ticks; return this; }
    public WeaponStats root(int ticks) { this.rootDurationTicks = ticks; return this; }

    public WeaponStats heal(double amount, double percentOfMax) {
        this.healAmount = amount; this.healPercentOfMax = percentOfMax; return this;
    }
    public WeaponStats damageReduction(double v) { this.damageReduction = v; return this; }
    public WeaponStats shield(double v) { this.shieldAmount = v; return this; }

    public WeaponStats multiHit(int count, double intervalTicks) {
        this.hitCount = count; this.hitIntervalTicks = intervalTicks; return this;
    }
    public WeaponStats maxTargets(int v) { this.maxTargets = v; return this; }

    public WeaponStats custom(String key, Object value) {
        if (key != null && value != null) custom.put(key, value);
        return this;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public double damage() { return damage; }
    public double damageScaling() { return damageScaling; }
    public double criticalChance() { return criticalChance; }
    public double criticalMultiplier() { return criticalMultiplier; }
    public double armorPenetration() { return armorPenetration; }
    public double attackSpeed() { return attackSpeed; }

    public double percentMaxHealth() { return percentMaxHealth; }
    public double percentCurrentHealth() { return percentCurrentHealth; }
    public double percentMissingHealth() { return percentMissingHealth; }
    public double percentDamageCap() { return percentDamageCap; }

    public double lifesteal() { return lifesteal; }
    public double lifestealOverhealCap() { return lifestealOverhealCap; }
    public double reflectPercent() { return reflectPercent; }
    public double reflectFlat() { return reflectFlat; }
    public int reflectDurationTicks() { return reflectDurationTicks; }

    public double cooldown() { return cooldown; }
    /** 左键技能冷却；未单独配置时回退 {@link #cooldown()}。 */
    public double cooldownLeft() { return cooldownLeft >= 0 ? cooldownLeft : cooldown; }
    /** 右键技能冷却；未单独配置时回退 {@link #cooldown()}。 */
    public double cooldownRight() { return cooldownRight >= 0 ? cooldownRight : cooldown; }
    public double onHitIntervalSeconds() { return onHitIntervalSeconds; }
    public double manaCost() { return manaCost; }
    public double staminaCost() { return staminaCost; }

    public double dashSpeed() { return dashSpeed; }
    public double dashDistance() { return dashDistance; }
    public double dashY() { return dashY; }
    public double blinkDistance() { return blinkDistance; }
    public double knockbackStrength() { return knockbackStrength; }
    public double pullStrength() { return pullStrength; }
    public double hopVelocity() { return hopVelocity; }
    public double hopForward() { return hopForward; }
    public double slamLaunchVelocity() { return slamLaunchVelocity; }
    public int slamApexTicks() { return slamApexTicks; }
    public double slamDownVelocity() { return slamDownVelocity; }
    public int slamTimeoutTicks() { return slamTimeoutTicks; }
    public double slamUppercutVelocity() { return slamUppercutVelocity; }
    public double slamSelfBounce() { return slamSelfBounce; }

    public double range() { return range; }
    public double aimRange() { return aimRange; }
    public double aimAngle() { return aimAngle; }
    public double aoeRadius() { return aoeRadius; }
    public double aoeDamageRatio() { return aoeDamageRatio; }

    public int slowDurationTicks() { return slowDurationTicks; }
    public int slowAmplifier() { return slowAmplifier; }
    public int stunDurationTicks() { return stunDurationTicks; }
    public int rootDurationTicks() { return rootDurationTicks; }

    public double damageReduction() { return damageReduction; }
    public double shieldAmount() { return shieldAmount; }
    public double healAmount() { return healAmount; }
    public double healPercentOfMax() { return healPercentOfMax; }

    public int hitCount() { return hitCount; }
    public double hitIntervalTicks() { return hitIntervalTicks; }
    public int maxTargets() { return maxTargets; }

    public String particle() { return particle; }
    public String sound() { return sound; }
    public float soundVolume() { return soundVolume; }
    public float soundPitch() { return soundPitch; }

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

    // ------------------------------------------------------------------
    // 伤害计算便捷
    // ------------------------------------------------------------------

    /** 用当前武器数值算一次完整物理伤害（暴击+护甲）。 */
    public double calculateHit(LivingEntity target, double power) {
        return com.skillcore.utils.DamageUtils.calculateFinalDamage(
                damage, damageScaling, power,
                criticalChance, criticalMultiplier,
                armorPenetration, target);
    }

    /** 额外百分比伤害（受 {@code percent-damage-cap} 上限约束）。 */
    public double calculatePercentBonus(LivingEntity target) {
        double bonus = com.skillcore.utils.PercentageDamageUtils.combine(
                target, 0, percentMaxHealth, percentMissingHealth)
                + com.skillcore.utils.PercentageDamageUtils.ofCurrentHealth(target, percentCurrentHealth);
        if (!Double.isFinite(bonus)) {
            return 0.0;
        }
        if (percentDamageCap > 0) {
            bonus = Math.min(bonus, percentDamageCap);
        }
        return Math.max(0.0, bonus);
    }

    /** 一次完整攻击总伤 = 物理 + 百分比（百分比部分已在 calculatePercentBonus 内限幅）。 */
    public double calculateTotal(LivingEntity target, double power) {
        return calculateHit(target, power) + calculatePercentBonus(target);
    }

    // ------------------------------------------------------------------
    // 配置读取
    // ------------------------------------------------------------------

    public static WeaponStats fromConfig(ConfigurationSection section) {
        WeaponStats stats = new WeaponStats();
        if (section == null) {
            return stats;
        }
        stats.damage = section.getDouble("damage", stats.damage);
        stats.damageScaling = section.getDouble("damage-scaling", section.getDouble("scaling", stats.damageScaling));
        stats.criticalChance = section.getDouble("critical-chance", section.getDouble("crit-chance", stats.criticalChance));
        stats.criticalMultiplier = section.getDouble("critical-multiplier", section.getDouble("crit-multiplier", stats.criticalMultiplier));
        stats.armorPenetration = section.getDouble("armor-penetration", section.getDouble("penetration", stats.armorPenetration));
        stats.attackSpeed = section.getDouble("attack-speed", stats.attackSpeed);

        stats.percentMaxHealth = section.getDouble("percent-max-health", stats.percentMaxHealth);
        stats.percentCurrentHealth = section.getDouble("percent-current-health", stats.percentCurrentHealth);
        stats.percentMissingHealth = section.getDouble("percent-missing-health", stats.percentMissingHealth);
        stats.percentDamageCap = section.getDouble("percent-damage-cap", section.getDouble("max-percent-damage", stats.percentDamageCap));

        stats.lifesteal = section.getDouble("lifesteal", stats.lifesteal);
        stats.lifestealOverhealCap = section.getDouble("lifesteal-overheal-cap", stats.lifestealOverhealCap);
        stats.reflectPercent = section.getDouble("reflect-percent", stats.reflectPercent);
        stats.reflectFlat = section.getDouble("reflect-flat", stats.reflectFlat);
        stats.reflectDurationTicks = section.getInt("reflect-duration-ticks", stats.reflectDurationTicks);

        stats.cooldown = section.getDouble("cooldown", stats.cooldown);
        stats.cooldownLeft = section.getDouble("cooldown-left", -1.0);
        stats.cooldownRight = section.getDouble("cooldown-right", -1.0);
        stats.onHitIntervalSeconds = section.getDouble("on-hit-interval", stats.onHitIntervalSeconds);
        stats.manaCost = section.getDouble("mana-cost", section.getDouble("mana", stats.manaCost));
        stats.staminaCost = section.getDouble("stamina-cost", stats.staminaCost);

        stats.dashSpeed = section.getDouble("dash-speed", stats.dashSpeed);
        stats.dashDistance = section.getDouble("dash-distance", stats.dashDistance);
        stats.dashY = section.getDouble("dash-y", stats.dashY);
        stats.blinkDistance = section.getDouble("blink-distance", stats.blinkDistance);
        stats.knockbackStrength = section.getDouble("knockback", section.getDouble("knockback-strength", stats.knockbackStrength));
        stats.pullStrength = section.getDouble("pull-strength", stats.pullStrength);
        stats.hopVelocity = section.getDouble("hop-velocity", stats.hopVelocity);
        stats.hopForward = section.getDouble("hop-forward", stats.hopForward);
        stats.slamLaunchVelocity = section.getDouble("slam-launch-velocity", stats.slamLaunchVelocity);
        stats.slamApexTicks = section.getInt("slam-apex-ticks", stats.slamApexTicks);
        stats.slamDownVelocity = section.getDouble("slam-down-velocity", stats.slamDownVelocity);
        stats.slamTimeoutTicks = section.getInt("slam-timeout-ticks", stats.slamTimeoutTicks);
        stats.slamUppercutVelocity = section.getDouble("slam-uppercut-velocity", stats.slamUppercutVelocity);
        stats.slamSelfBounce = section.getDouble("slam-self-bounce", stats.slamSelfBounce);

        stats.range = section.getDouble("range", stats.range);
        stats.aimRange = section.getDouble("aim-range", stats.aimRange);
        stats.aimAngle = section.getDouble("aim-angle", stats.aimAngle);
        stats.aoeRadius = section.getDouble("aoe-radius", stats.aoeRadius);
        stats.aoeDamageRatio = section.getDouble("aoe-damage-ratio", stats.aoeDamageRatio);

        stats.slowDurationTicks = section.getInt("slow-duration-ticks", section.getInt("slow-ticks", stats.slowDurationTicks));
        stats.slowAmplifier = section.getInt("slow-amplifier", stats.slowAmplifier);
        stats.stunDurationTicks = section.getInt("stun-duration-ticks", section.getInt("stun-ticks", stats.stunDurationTicks));
        stats.rootDurationTicks = section.getInt("root-duration-ticks", section.getInt("root-ticks", stats.rootDurationTicks));

        stats.damageReduction = section.getDouble("damage-reduction", stats.damageReduction);
        stats.shieldAmount = section.getDouble("shield", section.getDouble("shield-amount", stats.shieldAmount));
        stats.healAmount = section.getDouble("heal", section.getDouble("heal-amount", stats.healAmount));
        stats.healPercentOfMax = section.getDouble("heal-percent-of-max", stats.healPercentOfMax);

        stats.hitCount = section.getInt("hit-count", stats.hitCount);
        stats.hitIntervalTicks = section.getDouble("hit-interval-ticks", stats.hitIntervalTicks);
        stats.maxTargets = section.getInt("max-targets", stats.maxTargets);

        stats.particle = section.getString("particle", stats.particle);
        stats.sound = section.getString("sound", stats.sound);
        stats.soundVolume = (float) section.getDouble("sound-volume", stats.soundVolume);
        stats.soundPitch = (float) section.getDouble("sound-pitch", stats.soundPitch);

        // 自定义扩展：stats.custom.*
        ConfigurationSection custom = section.getConfigurationSection("custom");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                stats.custom.put(key, custom.get(key));
            }
        }
        stats.sanitize();
        return stats;
    }

    /**
     * 修正配置里的非法值（NaN / Infinity / 负数 / 超范围）。
     * <p>
     * 手写 yml 很容易出现 {@code cooldown: -1}、{@code damage: NaN} 这类值，
     * 直接参与运算会导致伤害 NaN、速度设置抛异常，这里统一收敛到安全范围。
     */
    private void sanitize() {
        // 上限定得比较宽（正常配置远低于此），只拦「配置写错一个数量级」导致的秒杀 / 无敌
        damage = clamp(damage, 0.0, 10_000.0);
        damageScaling = clamp(damageScaling, 0.0, 1_000.0);
        criticalChance = clamp(criticalChance, 0.0, 1.0);
        criticalMultiplier = Math.max(1.0, finite(criticalMultiplier, 1.5));
        armorPenetration = clamp(armorPenetration, 0.0, 1.0);
        attackSpeed = clamp(attackSpeed, 0.1, 10.0);

        percentMaxHealth = clamp(percentMaxHealth, 0.0, 1.0);
        percentCurrentHealth = clamp(percentCurrentHealth, 0.0, 1.0);
        percentMissingHealth = clamp(percentMissingHealth, 0.0, 1.0);
        percentDamageCap = clamp(percentDamageCap, 0.0, 100_000.0);

        lifesteal = clamp(lifesteal, 0.0, 1.0);
        lifestealOverhealCap = clamp(lifestealOverhealCap, 0.0, 10_000.0);
        reflectPercent = clamp(reflectPercent, 0.0, 1.0);
        reflectFlat = clamp(reflectFlat, 0.0, 10_000.0);
        reflectDurationTicks = clamp(reflectDurationTicks, 0, 20 * 60 * 10);

        cooldown = clamp(cooldown, 0.0, 86_400.0);
        // -1 表示「未单独配置，回退 cooldown」，保留；其他负数按 0 处理
        cooldownLeft = cooldownLeft < 0 ? -1.0 : clamp(cooldownLeft, 0.0, 86_400.0);
        cooldownRight = cooldownRight < 0 ? -1.0 : clamp(cooldownRight, 0.0, 86_400.0);
        manaCost = nonNegative(manaCost, 0.0);
        staminaCost = nonNegative(staminaCost, 0.0);
        onHitIntervalSeconds = clamp(onHitIntervalSeconds, 0.0, 60.0);

        dashSpeed = clamp(dashSpeed, 0.0, 10.0);
        dashDistance = clamp(dashDistance, 0.0, 100.0);
        dashY = clamp(dashY, -5.0, 5.0);
        blinkDistance = clamp(blinkDistance, 0.0, 64.0);
        knockbackStrength = clamp(knockbackStrength, 0.0, 10.0);
        pullStrength = clamp(pullStrength, 0.0, 10.0);

        hopVelocity = clamp(hopVelocity, -5.0, 5.0);
        hopForward = clamp(hopForward, -5.0, 5.0);
        slamLaunchVelocity = clamp(slamLaunchVelocity, -5.0, 5.0);
        slamApexTicks = clamp(slamApexTicks, 1, 200);
        slamDownVelocity = clamp(slamDownVelocity, -5.0, 5.0);
        slamTimeoutTicks = clamp(slamTimeoutTicks, 20, 1200);
        slamUppercutVelocity = clamp(slamUppercutVelocity, -5.0, 5.0);
        slamSelfBounce = clamp(slamSelfBounce, -5.0, 5.0);

        range = clamp(range, 0.0, 64.0);
        aimRange = clamp(aimRange, 0.0, 64.0);
        aimAngle = clamp(aimAngle, 0.0, 180.0);
        aoeRadius = clamp(aoeRadius, 0.0, 32.0);
        aoeDamageRatio = clamp(aoeDamageRatio, 0.0, 1.0);

        slowDurationTicks = clamp(slowDurationTicks, 0, 20 * 60 * 10);
        slowAmplifier = clamp(slowAmplifier, 0, 255);
        stunDurationTicks = clamp(stunDurationTicks, 0, 20 * 60 * 10);
        rootDurationTicks = clamp(rootDurationTicks, 0, 20 * 60 * 10);

        damageReduction = clamp(damageReduction, 0.0, 1.0);
        shieldAmount = clamp(shieldAmount, 0.0, 10_000.0);
        healAmount = clamp(healAmount, 0.0, 10_000.0);
        healPercentOfMax = clamp(healPercentOfMax, 0.0, 1.0);

        hitCount = clamp(hitCount, 1, 20);
        hitIntervalTicks = clamp(hitIntervalTicks, 0.0, 200.0);
        maxTargets = clamp(maxTargets, 1, 50);

        soundVolume = (float) clamp(soundVolume, 0.0, 10.0);
        soundPitch = (float) clamp(soundPitch, 0.5, 2.0);
    }

    private static double finite(double value, double def) {
        return Double.isFinite(value) ? value : def;
    }

    private static double nonNegative(double value, double def) {
        double v = finite(value, def);
        return Math.max(0.0, v);
    }

    private static double clamp(double value, double min, double max) {
        double v = finite(value, min);
        return Math.max(min, Math.min(max, v));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public WeaponStats copy() {
        WeaponStats s = new WeaponStats();
        s.damage = damage;
        s.damageScaling = damageScaling;
        s.criticalChance = criticalChance;
        s.criticalMultiplier = criticalMultiplier;
        s.armorPenetration = armorPenetration;
        s.attackSpeed = attackSpeed;
        s.percentMaxHealth = percentMaxHealth;
        s.percentCurrentHealth = percentCurrentHealth;
        s.percentMissingHealth = percentMissingHealth;
        s.percentDamageCap = percentDamageCap;
        s.lifesteal = lifesteal;
        s.lifestealOverhealCap = lifestealOverhealCap;
        s.reflectPercent = reflectPercent;
        s.reflectFlat = reflectFlat;
        s.reflectDurationTicks = reflectDurationTicks;
        s.cooldown = cooldown;
        s.cooldownLeft = cooldownLeft;
        s.cooldownRight = cooldownRight;
        s.onHitIntervalSeconds = onHitIntervalSeconds;
        s.manaCost = manaCost;
        s.staminaCost = staminaCost;
        s.dashSpeed = dashSpeed;
        s.dashDistance = dashDistance;
        s.dashY = dashY;
        s.blinkDistance = blinkDistance;
        s.knockbackStrength = knockbackStrength;
        s.pullStrength = pullStrength;
        s.hopVelocity = hopVelocity;
        s.hopForward = hopForward;
        s.slamLaunchVelocity = slamLaunchVelocity;
        s.slamApexTicks = slamApexTicks;
        s.slamDownVelocity = slamDownVelocity;
        s.slamTimeoutTicks = slamTimeoutTicks;
        s.slamUppercutVelocity = slamUppercutVelocity;
        s.slamSelfBounce = slamSelfBounce;
        s.range = range;
        s.aimRange = aimRange;
        s.aimAngle = aimAngle;
        s.aoeRadius = aoeRadius;
        s.aoeDamageRatio = aoeDamageRatio;
        s.slowDurationTicks = slowDurationTicks;
        s.slowAmplifier = slowAmplifier;
        s.stunDurationTicks = stunDurationTicks;
        s.rootDurationTicks = rootDurationTicks;
        s.damageReduction = damageReduction;
        s.shieldAmount = shieldAmount;
        s.healAmount = healAmount;
        s.healPercentOfMax = healPercentOfMax;
        s.hitCount = hitCount;
        s.hitIntervalTicks = hitIntervalTicks;
        s.maxTargets = maxTargets;
        s.particle = particle;
        s.sound = sound;
        s.soundVolume = soundVolume;
        s.soundPitch = soundPitch;
        s.custom.putAll(custom);
        return s;
    }
}
