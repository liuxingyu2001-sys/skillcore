package com.skillcore.weapon.skills;

import com.skillcore.effect.EffectContext;
import com.skillcore.effect.Effects;
import com.skillcore.effect.SkillEffect;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.SoundUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 血月之镰专属特效 — 深红 / 血光 / 月白三色系 + 灵魂粒子 + 新月刀光，分阶段渲染。
 * <p>
 * 主题色取自武器显示名渐变（#7A0B1E → #FF3B5C → #FFB4C0）：
 * 暗血红（BLOOD）、血光（CRIMSON）、月白（MOON）、暗紫（DARK）。
 * <p>
 * 所有方法只负责「表现」，伤害与目标判定在技能类里；粒子开关统一走
 * {@link ParticleUtils}（受 {@code effects.enabled} 控制），显示物由技能类自行判断。
 */
public final class BloodMoonScytheFx {

    /** 暗血红（刀身主体） */
    private static final Color BLOOD = Color.fromRGB(0x8B, 0x0A, 0x1A);
    /** 血光（高亮） */
    private static final Color CRIMSON = Color.fromRGB(0xFF, 0x2E, 0x4D);
    /** 月白（刀锋反光） */
    private static final Color MOON = Color.fromRGB(0xF2, 0xE6, 0xFF);
    /** 暗紫（阴影） */
    private static final Color DARK = Color.fromRGB(0x3B, 0x0A, 0x2A);

    private BloodMoonScytheFx() {
    }

    // ------------------------------------------------------------------
    // 右键「血月回旋」
    // ------------------------------------------------------------------

    /** 起手：血色新月扩散 + 灵魂上浮 + 投掷音。 */
    public static void castBurst(Location eye, Vector direction) {
        if (eye == null || eye.getWorld() == null) return;
        Effects.parallel(
                Effects.expandingRing(Particle.SWEEP_ATTACK, 0.4, 1.3, 18, 4),
                Effects.expandingRing(Particle.SOUL_FIRE_FLAME, 0.5, 1.0, 20, 5),
                Effects.dust(CRIMSON, 1.2f, 26, 0.4),
                Effects.dust(DARK, 0.9f, 16, 0.3),
                Effects.dust(MOON, 0.8f, 10, 0.2),
                Effects.particle(Particle.SOUL, 14, 0.35),
                Effects.particle(Particle.CRIT, 12, 0.3),
                Effects.sound(Sound.ITEM_TRIDENT_THROW, 1.0f, 1.35f),
                Effects.sound(Sound.ENTITY_PHANTOM_SWOOP, 0.9f, 0.7f)
        ).play(EffectContext.of(eye, direction));
    }

    /** 飞行中每 tick：血色拖尾 + 灵魂；每 2 tick 垂直于飞行方向转一圈刀环。 */
    public static void flightTick(Location location, Vector direction, int tick) {
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.dust(jitter(location, 0.12), CRIMSON, 0.8f, 1);
        ParticleUtils.dust(jitter(location, 0.18), BLOOD, 0.7f, 1);
        ParticleUtils.spawn(location, Particle.SOUL, 1, 0.05, 0.05, 0.05, 0.01);
        if (tick % 2 == 0) {
            // 以飞行方向为轴画环 —— 像旋转的镰刃扫出的圆
            ParticleUtils.ring(location, 0.85, Particle.SWEEP_ATTACK, 12, direction);
            ParticleUtils.dust(jitter(location, 0.3), MOON, 0.6f, 1);
        }
        if (tick % 4 == 0) {
            ParticleUtils.spawn(location, Particle.CRIT, 3, 0.15, 0.15, 0.15, 0.03);
        }
    }

    /** 命中反馈：目标僵直 + 白闪 + 多层爆环 + 双重音效。 */
    public static void impact(Location location, LivingEntity victim) {
        if (victim != null && !victim.isDead()) {
            // 打击感核心：受击动画 + 2 tick 僵直，命中瞬间「顿一下」
            victim.playHurtAnimation(0.0f);
            victim.setNoActionTicks(2);
        }
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.spawn(location, Particle.SWEEP_ATTACK, 6, 0.4, 0.3, 0.4, 0.0);
        ParticleUtils.ring(location.clone().add(0, 0.1, 0), 0.9, Particle.SOUL_FIRE_FLAME, 14,
                new Vector(0, 1, 0));
        ParticleUtils.dust(jitter(location, 0.25), CRIMSON, 1.1f, 10);
        ParticleUtils.dust(jitter(location, 0.2), MOON, 0.9f, 6);
        ParticleUtils.spawn(location.clone().add(0, 1.0, 0), Particle.SOUL, 5, 0.2, 0.3, 0.2, 0.02);
        SoundUtils.play(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.35f);
        SoundUtils.play(location, Sound.ENTITY_PHANTOM_BITE, 0.45f, 0.8f);
    }

    /** 飞满距离折返：脚下炸开一圈灵魂新月。 */
    public static void turnPoint(Location location) {
        if (location == null || location.getWorld() == null) return;
        Effects.expandingRing(Particle.SOUL_FIRE_FLAME, 0.3, 1.3, 16, 4).play(location);
        Effects.dust(CRIMSON, 1.3f, 18, 0.35).play(location);
        ParticleUtils.spawn(location, Particle.SOUL, 12, 0.2, 0.2, 0.2, 0.05);
        SoundUtils.play(location, Sound.ITEM_TRIDENT_RETURN, 0.9f, 0.7f);
    }

    /** 镰刀收回手里：月白尘爆 + 收刀音。 */
    public static void recall(Location location) {
        if (location == null || location.getWorld() == null) return;
        Effects.parallel(
                Effects.dust(MOON, 1.0f, 16, 0.4),
                Effects.dust(CRIMSON, 0.9f, 10, 0.3),
                Effects.particle(Particle.SOUL, 8, 0.3),
                Effects.sound(Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.2f)
        ).play(location);
    }

    /** 锁定目标时的标记环（飞行中每 4 tick 一次，提示「它被盯上了」）。 */
    public static void lockMarker(Location location) {
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.ring(location.clone().add(0, 0.2, 0), 0.8,
                Particle.SOUL_FIRE_FLAME, 10, new Vector(0, 1, 0));
        ParticleUtils.dust(jitter(location.clone().add(0, 1.0, 0), 0.15), CRIMSON, 0.7f, 2);
    }

    // ------------------------------------------------------------------
    // 左键「死神收割」
    // ------------------------------------------------------------------

    /** 挥砍起手：三层交错新月刀光 + 地面灵魂环。 */
    public static void sweepCast(Location eye, Vector direction, Location ground) {
        if (eye == null || eye.getWorld() == null) return;
        Vector dir = direction == null ? new Vector(0, 0, 1) : direction.clone();
        if (dir.lengthSquared() < 1.0e-8) {
            dir = eye.getDirection();
        }
        if (dir.lengthSquared() < 1.0e-8) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Location base = eye.clone().add(dir.clone().multiply(1.1));
        // 三道半径不同的弧，颜色由深到浅 —— 形成有纵深的刀光
        dustArc(base, dir, 1.7, 120.0, CRIMSON, 14);
        dustArc(base, dir, 2.35, 115.0, MOON, 12);
        dustArc(base, dir, 3.0, 105.0, DARK, 10);
        ParticleUtils.facingArc(base, dir, 2.2, 120.0, Particle.SWEEP_ATTACK, 8);
        ParticleUtils.spawn(base, Particle.CRIT, 10, 1.0, 1.2, 1.0, 0.02);
        if (ground != null && ground.getWorld() != null) {
            ParticleUtils.ring(ground, 1.5, Particle.SOUL_FIRE_FLAME, 20, new Vector(0, 1, 0));
            ParticleUtils.dust(jitter(ground, 0.6), CRIMSON, 1.0f, 12);
            ParticleUtils.spawn(ground.clone().add(0, 0.2, 0), Particle.SWEEP_ATTACK, 4,
                    1.2, 0.2, 1.2, 0.0);
        }
        SoundUtils.play(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.1f);
        SoundUtils.play(eye, Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 0.6f);
        SoundUtils.play(eye, Sound.ENTITY_PHANTOM_BITE, 0.5f, 0.9f);
    }

    /** 收割命中单个目标：脚下血环 + 灵魂上浮。 */
    public static void sweepHit(Location location) {
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.ring(location.clone().add(0, 0.1, 0), 0.7,
                Particle.SOUL_FIRE_FLAME, 12, new Vector(0, 1, 0));
        ParticleUtils.dust(jitter(location.clone().add(0, 1.0, 0), 0.2), CRIMSON, 1.0f, 8);
        ParticleUtils.spawn(location.clone().add(0, 1.0, 0), Particle.SOUL, 5, 0.2, 0.3, 0.2, 0.02);
        SoundUtils.play(location, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.25f);
    }

    /** 处决：音爆环 + 双爆环 + 处决重音。 */
    public static void execute(Location location) {
        if (location == null || location.getWorld() == null) return;
        Effects.parallel(
                Effects.particle(Particle.SONIC_BOOM, 1),
                Effects.expandingRing(Particle.SOUL_FIRE_FLAME, 0.4, 1.8, 22, 5),
                Effects.expandingRing(Particle.SWEEP_ATTACK, 0.3, 1.4, 16, 4),
                Effects.dust(CRIMSON, 1.5f, 24, 0.45),
                Effects.dust(MOON, 1.1f, 14, 0.3),
                Effects.sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.75f),
                Effects.sound(Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.5f)
        ).play(location);
    }

    /** 引爆血月印记：中等规模的血环 + 灵魂爆（比处决轻，避免盖过处决反馈）。 */
    public static void detonate(Location location) {
        if (location == null || location.getWorld() == null) return;
        Effects.parallel(
                Effects.expandingRing(Particle.SOUL_FIRE_FLAME, 0.3, 1.2, 16, 3),
                Effects.dust(CRIMSON, 1.2f, 16, 0.35),
                Effects.dust(MOON, 0.9f, 8, 0.2),
                Effects.particle(Particle.SOUL, 8, 0.3),
                Effects.particle(Particle.SWEEP_ATTACK, 4, 0.25),
                Effects.sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.15f)
        ).play(location);
    }

    /** 击杀回血：生命汲取粒子 + 轻快音效。 */
    public static void healBurst(Location location) {
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.spawn(location, Particle.TOTEM_OF_UNDYING, 6, 0.3, 0.4, 0.3, 0.05);
        SoundUtils.play(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
    }

    /** 普攻/技能给目标挂「血月印记」时的提示粒子。 */
    public static void markApplied(Location location) {
        if (location == null || location.getWorld() == null) return;
        ParticleUtils.spawn(location.clone().add(0, 1.0, 0), Particle.SOUL, 4, 0.2, 0.3, 0.2, 0.0);
        ParticleUtils.dust(jitter(location.clone().add(0, 0.2, 0), 0.18), CRIMSON, 0.8f, 4);
    }

    /** 击杀：灵魂被抽回施法者的连线 + 拾取音。 */
    public static void killSoul(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return;
        if (!from.getWorld().equals(to.getWorld())) return;
        ParticleUtils.line(from, to, Particle.SOUL, 0.35);
        ParticleUtils.spawn(to.clone().add(0, 1.0, 0), Particle.TOTEM_OF_UNDYING, 5,
                0.25, 0.3, 0.25, 0.05);
        SoundUtils.play(to, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 1.4f);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 沿朝向画一条「彩色尘弧」（dust 需要逐点带 data，不能用纯 Particle 版）。 */
    private static void dustArc(Location center, Vector direction, double radius, double arcDeg,
                                Color color, int points) {
        List<Location> pointsList = ParticleUtils.facingArcPoints(center, direction, radius, arcDeg, points);
        for (Location point : pointsList) {
            ParticleUtils.dust(point, color, 0.85f, 1);
        }
    }

    /** 随机抖动位置（避免粒子总在同一坐标点成一团）。 */
    private static Location jitter(Location location, double spread) {
        return location.clone().add(
                (Math.random() * 2 - 1) * spread,
                (Math.random() * 2 - 1) * spread,
                (Math.random() * 2 - 1) * spread);
    }

    /** 供技能类复用的组合特效（保留入口，方便后续调整整体手感）。 */
    public static SkillEffect none() {
        return Effects.none();
    }
}
