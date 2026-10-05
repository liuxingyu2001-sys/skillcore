package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 高层技能效果组合工具 — 把常用效果打包成一次调用，写技能更省事。
 * <p>
 * 示例:
 * <pre>
 * SkillEffects.damageAndLifesteal(caster, target, 15, 0.2);
 * SkillEffects.aoeAround(target.getLocation(), 4, 8, caster);
 * SkillEffects.dashForward(player, 1.5);
 * </pre>
 */
public final class SkillEffects {

    private SkillEffects() {
    }

    // ------------------------------------------------------------------
    // 伤害 + 吸血
    // ------------------------------------------------------------------

    /**
     * 造成伤害并按比例吸血。
     *
     * @return 实际造成的伤害
     */
    public static double damageAndLifesteal(LivingEntity caster, LivingEntity target, double damage, double lifestealPercent) {
        SkillDamageUtils.damage(target, damage, caster, "skill-effects");
        LifestealUtils.healByDamagePercent(caster, damage, lifestealPercent);
        return damage;
    }

    /**
     * 完整攻击管线: 基础伤害 + 暴击 + 护甲 + 吸血。
     */
    public static double attack(
            LivingEntity caster,
            LivingEntity target,
            double baseDamage,
            double criticalChance,
            double criticalMultiplier,
            double armorPenetration,
            double lifestealPercent
    ) {
        double dealt = DamageUtils.calculateFinalDamage(
                baseDamage, 0, 1.0, criticalChance, criticalMultiplier, armorPenetration, target);
        SkillDamageUtils.damage(target, dealt, caster, "skill-effects");
        if (lifestealPercent > 0) {
            LifestealUtils.healByDamagePercent(caster, dealt, lifestealPercent);
        }
        return dealt;
    }

    /**
     * 百分比最大生命伤害 + 吸血。
     */
    public static double percentDamageAndLifesteal(
            LivingEntity caster,
            LivingEntity target,
            double percentOfMaxHp,
            double maxDamage,
            double lifestealPercent
    ) {
        double damage = PercentageDamageUtils.ofMaxHealthCapped(target, percentOfMaxHp, maxDamage);
        return damageAndLifesteal(caster, target, damage, lifestealPercent);
    }

    // ------------------------------------------------------------------
    // AOE
    // ------------------------------------------------------------------

    /**
     * 对半径内敌人造成伤害 (排除施法者)。
     *
     * @return 被击中的实体列表
     */
    public static List<LivingEntity> aoeAround(Location center, double radius, double damage, LivingEntity caster) {
        List<LivingEntity> targets = TargetFilter.filter(caster,
                AreaUtils.getSphere(center, radius, e -> TargetFilter.isAttackable(caster, e)));
        for (LivingEntity target : targets) {
            SkillDamageUtils.damage(target, damage, caster, "skill-effects");
        }
        return targets;
    }

    /**
     * 对半径内敌人造成百分比最大生命伤害。
     */
    public static List<LivingEntity> aoePercentAround(Location center, double radius, double percent, double maxDamage, LivingEntity caster) {
        List<LivingEntity> targets = TargetFilter.filter(caster,
                AreaUtils.getSphere(center, radius, e -> TargetFilter.isAttackable(caster, e)));
        for (LivingEntity target : targets) {
            PercentageDamageUtils.dealPercentOfMaxHealth(caster, target, percent, maxDamage);
        }
        return targets;
    }

    // ------------------------------------------------------------------
    // 位移
    // ------------------------------------------------------------------

    public static void dashForward(LivingEntity entity, double speed) {
        DisplacementUtils.dash(entity, speed, 0.15);
        DisplacementUtils.playDashEffect(entity.getLocation());
    }

    public static void blinkForward(LivingEntity entity, double distance) {
        DisplacementUtils.blinkForward(entity, distance);
    }

    public static void knockbackAway(LivingEntity target, LivingEntity source, double strength) {
        DisplacementUtils.knockbackFrom(target, source, strength, 0.35);
    }

    public static void pullTo(LivingEntity target, LivingEntity puller, double strength) {
        DisplacementUtils.pullToEntity(target, puller, strength);
    }

    // ------------------------------------------------------------------
    // 瞄准
    // ------------------------------------------------------------------

    public static LivingEntity aimOrNearest(Player player, double maxDistance) {
        return AimUtils.aimAssist(player, maxDistance, 35);
    }

    public static LivingEntity nearestEnemy(LivingEntity origin, double radius) {
        return AimUtils.getNearest(origin, radius, e -> EntityUtils.isEnemy(origin, e));
    }

    // ------------------------------------------------------------------
    // 反伤 / 增益
    // ------------------------------------------------------------------

    public static void grantReflect(LivingEntity entity, double percent, double seconds) {
        ReflectDamageUtils.addReflectBuff(entity, percent, 0, (long) (seconds * 20));
    }

    public static void grantLifestealBuff(Player player, double percent, double seconds) {
        // 由 CombatListener 配合; 这里用 PDC/Map 简化为直接设置
        // 实际项目可挂到 PlayerCombatProfile
        // 占位: 通过技能系统内部状态
    }

    // ------------------------------------------------------------------
    // 控制
    // ------------------------------------------------------------------

    public static void slow(LivingEntity target, double seconds, int amplifier) {
        PotionUtils.slowness(target, (int) (seconds * 20), amplifier);
    }

    public static void stun(LivingEntity target, double seconds) {
        PotionUtils.stun(target, (int) (seconds * 20));
    }

    public static void root(LivingEntity target, double seconds) {
        PotionUtils.root(target, (int) (seconds * 20));
    }

    // ------------------------------------------------------------------
    // 回复
    // ------------------------------------------------------------------

    public static double heal(LivingEntity entity, double amount) {
        return LifestealUtils.heal(entity, amount);
    }

    public static double healPercentOfMax(LivingEntity entity, double percent) {
        return LifestealUtils.healByMaxHealthPercent(entity, percent);
    }

    // ------------------------------------------------------------------
    // 可见 / 可听
    // ------------------------------------------------------------------

    public static void castFx(LivingEntity caster, LivingEntity target) {
        ParticleUtils.line(caster.getEyeLocation(), target.getEyeLocation(), org.bukkit.Particle.CRIT, 0.35);
        ParticleUtils.hitMarker(target, org.bukkit.Particle.CRIT);
        if (caster instanceof Player player) {
            SoundUtils.hit(player);
        }
    }

    public static void hitFx(LivingEntity target) {
        ParticleUtils.hitMarker(target, org.bukkit.Particle.CRIT);
    }

    /**
     * 批量伤害 (list)。
     */
    public static void damageAll(LivingEntity caster, List<LivingEntity> targets, double damage) {
        if (targets == null) {
            return;
        }
        for (LivingEntity target : targets) {
            SkillDamageUtils.damage(target, damage, caster, "skill-effects");
        }
    }

    /**
     * 批量百分比伤害 (list)。
     */
    public static void percentDamageAll(LivingEntity caster, List<LivingEntity> targets, double percent, double maxDamage) {
        if (targets == null) {
            return;
        }
        for (LivingEntity target : targets) {
            PercentageDamageUtils.dealPercentOfMaxHealth(caster, target, percent, maxDamage);
        }
    }
}
