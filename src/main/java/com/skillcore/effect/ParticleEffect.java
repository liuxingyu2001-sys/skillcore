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
        // 计数为负会让服务端抛错；偏移/额外参数出现 NaN 会产生无效数据包
        this.count = Math.max(0, Math.min(count, 4096));
        this.offsetX = Double.isFinite(offsetX) ? offsetX : 0.0;
        this.offsetY = Double.isFinite(offsetY) ? offsetY : 0.0;
        this.offsetZ = Double.isFinite(offsetZ) ? offsetZ : 0.0;
        this.extra = Double.isFinite(extra) ? extra : 0.0;
    }

    @Override
    public void play(EffectContext context) {
        if (particle == null || !context.valid()) return;
        com.skillcore.utils.ParticleUtils.spawn(
                context.location(), particle, count, offsetX, offsetY, offsetZ, extra);
    }
}
