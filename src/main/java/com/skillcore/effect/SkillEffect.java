package com.skillcore.effect;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * 技能特效接口 — 一个特效 = 一次 {@link #play(EffectContext)}。
 * <p>
 * 函数式接口：可以直接写 lambda，也可以用 {@link Effects} 工厂组合内置特效。
 * <pre>
 * SkillEffect fx = Effects.ring(Particle.END_ROD, 2.0, 24)
 *         .andThen(Effects.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f));
 * fx.play(player.getLocation());
 * </pre>
 */
@FunctionalInterface
public interface SkillEffect {

    /** 播放特效（默认主线程调用）。 */
    void play(EffectContext context);

    /** 在指定位置播放。 */
    default void play(Location location) {
        play(EffectContext.of(location));
    }

    /** 在指定位置播放，并带上施法者。 */
    default void play(LivingEntity caster, Location location) {
        play(EffectContext.of(location).withCaster(caster));
    }

    /** 追加一个并行的特效，返回组合特效。 */
    default SkillEffect andThen(SkillEffect next) {
        return Effects.parallel(this, next);
    }

    /** 延迟若干 tick 后播放。 */
    default SkillEffect delayed(long ticks) {
        return Effects.delayed(this, ticks);
    }
}
