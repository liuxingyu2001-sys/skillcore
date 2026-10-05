package com.skillcore.weapon.skills;

import com.skillcore.effect.Effects;
import com.skillcore.effect.SkillEffect;
import com.skillcore.utils.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

/**
 * 风暴战锤专属特效 — 紫白渐变 + 电光 + 风涡，分阶段渲染。
 * <p>
 * 主题色取自武器显示名渐变 (#C9A7FF → #F2E6FF)，辅以风暴青点缀。
 */
public final class StormHammerFx {

    // 主题色
    private static final Color LIGHT = Color.fromRGB(0xF2, 0xE6, 0xFF);   // 亮白紫
    private static final Color PURPLE = Color.fromRGB(0xC9, 0xA7, 0xFF);  // 主紫
    private static final Color VIOLET = Color.fromRGB(0x9F, 0x6B, 0xFF);  // 深紫
    private static final Color CYAN = Color.fromRGB(0x7E, 0xE8, 0xFF);    // 风暴青

    private StormHammerFx() {
    }

    /** 跃起瞬间：风涡 + 电光 + 彩色尘 + 双环。 */
    public static SkillEffect castBurst() {
        return Effects.parallel(
                Effects.dust(PURPLE, 1.4f, 40, 0.9),
                Effects.dust(CYAN, 1.1f, 28, 0.8),
                Effects.dust(LIGHT, 1.0f, 20, 0.7),
                Effects.particle(Particle.ELECTRIC_SPARK, 24, 0.9),
                Effects.particle(Particle.GUST, 18, 0.7),
                Effects.ring(Particle.END_ROD, 1.4, 24),
                Effects.ring(Particle.SMALL_GUST, 0.9, 18),
                Effects.sound(Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.2f, 1.7f),
                Effects.sound(Sound.ITEM_MACE_SMASH_AIR, 1.0f, 1.4f));
    }

    /** 右键小跳：轻量风爆。 */
    public static SkillEffect hopBurst() {
        return Effects.parallel(
                Effects.dust(PURPLE, 1.0f, 16, 0.5),
                Effects.ring(Particle.SMALL_GUST, 0.8, 14),
                Effects.particle(Particle.ELECTRIC_SPARK, 10, 0.5),
                Effects.particle(Particle.GUST, 8, 0.4),
                Effects.sound(Sound.ENTITY_WIND_CHARGE_THROW, 0.9f, 1.3f));
    }

    /** 小跳拖尾（位移后若干 tick 调用）。 */
    public static void hopTrail(Location loc) {
        ParticleUtils.trail(loc, Particle.SMALL_GUST, 3, 0.35);
        ParticleUtils.dust(loc.clone().add(0, 0.3, 0), PURPLE, 0.9f, 1);
    }

    /** 上升过程逐 tick：围绕玩家的螺旋风涡 + 电光 + 拖尾。 */
    public static void ascendTick(Location loc, int tick) {
        double radius = 1.0 + tick * 0.15;
        ParticleUtils.helix(loc.clone().add(0, -0.3, 0), radius, 2.8, Particle.ELECTRIC_SPARK, 18);
        ParticleUtils.helix(loc.clone().add(0, -0.3, 0), radius * 0.6, 2.4, Particle.SMALL_GUST, 12);
        ParticleUtils.dust(loc.clone().add(0, 0.5, 0), PURPLE, 1.1f, 3);
        ParticleUtils.dust(loc.clone().add(0, 1.1, 0), CYAN, 0.9f, 2);
        ParticleUtils.trail(loc, Particle.SMALL_GUST, 4, 0.5);
        if (tick % 2 == 0) {
            ParticleUtils.ring(loc, radius + 0.4, Particle.END_ROD, 12, new Vector(0, 1, 0));
        }
    }

    /** 下砸过程逐 tick：上方风锥 + 坠落螺旋 + 随机天雷。 */
    public static void descentTick(Location loc, int tick) {
        ParticleUtils.cone(loc.clone().add(0, 3.4, 0), new Vector(0, -1, 0), 26, 3.4, Particle.GUST, 8);
        ParticleUtils.helix(loc.clone().add(0, 2.2, 0), 1.4, 2.0, Particle.ELECTRIC_SPARK, 10);
        ParticleUtils.trail(loc, Particle.SMALL_GUST, 3, 0.4);
        ParticleUtils.dust(loc.clone().add(0, 1.0, 0), VIOLET, 1.0f, 2);
        if (Math.random() < 0.25) {
            ParticleUtils.bolt(loc.clone().add(0, 5.0, 0), loc.clone().add(0, 0.7, 0),
                    Particle.ELECTRIC_SPARK, 1.2, 6);
        }
    }

    /** 落地冲击：多重冲击波 + 天雷 + 裂纹 + 爆炸球 + 尘土。 */
    public static SkillEffect impact(double radius) {
        return Effects.parallel(
                Effects.expandingRing(Particle.ELECTRIC_SPARK, 0.6, radius * 1.25, 40, 6),
                Effects.expandingRing(Particle.GUST, 0.8, radius * 1.5, 28, 8),
                Effects.expandingRing(Particle.END_ROD, 0.5, radius * 1.1, 32, 5),
                Effects.explosion(Particle.END_ROD, Particle.EXPLOSION, radius),
                Effects.groundShatter(radius * 0.85),
                Effects.dust(LIGHT, 1.6f, 28, radius * 0.5),
                Effects.dust(PURPLE, 1.3f, 40, radius * 0.6),
                Effects.dust(CYAN, 1.1f, 24, radius * 0.5),
                Effects.particle(Particle.CLOUD, 24, radius * 0.5),
                Effects.particle(Particle.ELECTRIC_SPARK, 30, radius * 0.7),
                Effects.skyStrikes(9.0, 4, radius * 0.8, Particle.ELECTRIC_SPARK, 1.4, 8),
                Effects.skyStrikesDust(8.0, 3, radius * 0.6, CYAN, 1.0f, 2, 1.4, 8),
                Effects.groundCracks(radius * 1.1, 8, Particle.END_ROD, 0.8),
                Effects.sound(Sound.ITEM_MACE_SMASH_GROUND, 1.4f, 0.9f),
                Effects.sound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f),
                Effects.sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.9f));
    }
}
