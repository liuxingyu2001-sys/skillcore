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
        this.volume = volume;
        this.pitch = pitch;
        this.radius = radius;
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
