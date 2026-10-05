package com.skillcore.effect;

import org.bukkit.Particle;

/**
 * 粒子特效（点/爆发）。更复杂的形状请用 {@link Effects} 的 ring/sphere/beam 等工厂方法。
 */
public final class ParticleEffect implements SkillEffect {

    private final Particle particle;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double extra;

    public ParticleEffect(Particle particle, int count,
                          double offsetX, double offsetY, double offsetZ, double extra) {
        this.particle = particle;
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.extra = extra;
    }

    @Override
    public void play(EffectContext context) {
        if (particle == null || !context.valid()) return;
        com.skillcore.utils.ParticleUtils.spawn(
                context.location(), particle, count, offsetX, offsetY, offsetZ, extra);
    }
}
