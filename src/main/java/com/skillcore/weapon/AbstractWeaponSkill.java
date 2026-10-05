package com.skillcore.weapon;

import org.bukkit.entity.LivingEntity;

import java.util.List;

/**
 * 武器技能基类 — 写新技能武器时继承它，数值自动走 {@link WeaponStats}。
 * <pre>
 * // 例：利刃突刺（右键向前突刺并伤害路径敌人）
 * public class BladeDashSkill extends AbstractWeaponSkill {
 *     public BladeDashSkill() { super("BLADE_DASH"); }
 *
 *     {@literal @}Override
 *     public void onRightClick(WeaponContext ctx) {
 *         ctx.dashForward();                 // 用 stats.dashSpeed / dashY
 *         List&lt;LivingEntity&gt; hit = slashAlongDash(ctx);
 *         for (LivingEntity e : hit) {
 *             double d = ctx.damage(e);      // 用 stats.damage / 暴击 / 穿透
 *             ctx.healSelfByDamage(d);       // 用 stats.lifesteal
 *         }
 *         ctx.castFx();
 *     }
 * }
 * </pre>
 */
public abstract class AbstractWeaponSkill implements WeaponSkill {

    private final String type;

    protected AbstractWeaponSkill(String type) {
        this.type = type;
    }

    @Override
    public String getType() {
        return type;
    }

    // ------------------------------------------------------------------
    // 内置组合技，写技能时直接调
    // ------------------------------------------------------------------

    /** 向前突刺并对路径上敌人造成伤害，返回命中列表。 */
    protected List<LivingEntity> dashSlash(WeaponContext ctx) {
        var player = ctx.player();
        var stats = ctx.stats();
        // 记录起点
        var start = player.getLocation().clone();
        ctx.dashForward();
        // 突刺后取前方 range 内敌人
        double reach = Math.max(stats.range(), stats.dashDistance());
        var targets = com.skillcore.utils.AreaUtils.getCylinder(
                player.getLocation(), reach, 2.5,
                e -> com.skillcore.utils.EntityUtils.isEnemy(player, e));
        // 只打不超过 maxTargets
        int max = Math.max(1, stats.maxTargets());
        if (targets.size() > max) {
            targets = targets.subList(0, max);
        }
        for (LivingEntity e : targets) {
            ctx.damage(e);
            ctx.knockback(e);
        }
        com.skillcore.utils.ParticleUtils.line(start, player.getLocation(),
                org.bukkit.Particle.CRIT, 0.3);
        return targets;
    }

    /** 对当前目标的一次完整连击（伤害+吸血+命中特效）。 */
    protected double strike(WeaponContext ctx) {
        LivingEntity victim = ctx.aimTarget();
        if (victim == null) return 0;
        double dealt = ctx.damage(victim);
        ctx.healSelfByDamage(dealt);
        ctx.hitFx(victim);
        return dealt;
    }

    /** 多目标横扫。 */
    protected List<LivingEntity> sweep(WeaponContext ctx) {
        LivingEntity center = ctx.aimTarget();
        if (center == null) {
            return java.util.List.of();
        }
        List<LivingEntity> hit = ctx.aoeFrom(center, ctx.stats().aoeRadius());
        for (LivingEntity e : hit) {
            double d = ctx.damage(e);
            ctx.healSelfByDamage(d);
        }
        return hit;
    }

    /** 突进到目标并造成伤害（位移+伤害一体）。 */
    protected double chargeStrike(WeaponContext ctx) {
        LivingEntity victim = ctx.aimAssist();
        if (victim == null) return 0;
        ctx.dashTowards(victim.getLocation());
        double dealt = ctx.damage(victim);
        ctx.knockback(victim);
        ctx.healSelfByDamage(dealt);
        ctx.lineToTarget();
        return dealt;
    }

    /** 百分比生命打击（打高血量目标）。 */
    protected double percentStrike(WeaponContext ctx) {
        LivingEntity victim = ctx.aimTarget();
        if (victim == null) return 0;
        double dealt = ctx.damagePercent(victim);
        ctx.healSelfByDamage(dealt);
        return dealt;
    }

    /** 控制打击：伤害+减速/眩晕。 */
    protected double controlStrike(WeaponContext ctx) {
        double dealt = strike(ctx);
        if (ctx.stats().stunDurationTicks() > 0) {
            ctx.stunTarget();
        } else if (ctx.stats().rootDurationTicks() > 0) {
            ctx.rootTarget();
        } else {
            ctx.slowTarget();
        }
        return dealt;
    }

    /** 闪现 + AOE。 */
    protected List<LivingEntity> blinkBurst(WeaponContext ctx) {
        ctx.blinkForward();
        return ctx.aoeAtAim(ctx.stats().aoeRadius());
    }

    /** 自我回复。 */
    protected void selfHeal(WeaponContext ctx) {
        ctx.healSelf();
    }
}
