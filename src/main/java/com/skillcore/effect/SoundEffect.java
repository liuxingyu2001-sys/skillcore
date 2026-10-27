package com.skillcore.effect;

import com.skillcore.utils.SoundUtils;
import org.bukkit.Sound;

/**
 * 音效特效。
 */
public final class SoundEffect implements SkillEffect {

    private final Sound sound;
    private final float volume;
    private final float pitch;
    private final double radius;

    public SoundEffect(Sound sound, float volume, float pitch, double radius) {
        this.sound = sound;
        // 音量 0~10、音调 0~2（与原版一致），非法值回退默认
        this.volume = Float.isFinite(volume) && volume >= 0 ? Math.min(volume, 10.0f) : 1.0f;
        this.pitch = Float.isFinite(pitch) && pitch > 0 ? Math.min(pitch, 2.0f) : 1.0f;
        this.radius = Double.isFinite(radius) ? radius : 0.0;
    }

    @Override
    public void play(EffectContext context) {
        if (!EffectSettings.effectsEnabled() || sound == null || !context.valid()) {
            return;
        }
        if (radius > 0) {
            SoundUtils.playRadius(context.location(), sound, volume, pitch, radius);
        } else {
            SoundUtils.play(context.location(), sound, volume, pitch);
        }
    }
}
