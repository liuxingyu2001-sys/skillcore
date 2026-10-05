package com.skillcore.effect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 组合特效 — 同时播放多个子特效。
 */
public final class CompositeEffect implements SkillEffect {

    private final List<SkillEffect> children;

    public CompositeEffect(SkillEffect... effects) {
        this.children = new ArrayList<>();
        if (effects != null) {
            for (SkillEffect e : effects) {
                if (e != null) children.add(e);
            }
        }
    }

    public CompositeEffect(List<SkillEffect> effects) {
        this.children = effects == null ? new ArrayList<>() : new ArrayList<>(effects);
    }

    public CompositeEffect add(SkillEffect effect) {
        if (effect != null) children.add(effect);
        return this;
    }

    public List<SkillEffect> children() {
        return List.copyOf(children);
    }

    @Override
    public void play(EffectContext context) {
        for (SkillEffect child : children) {
            try {
                child.play(context);
            } catch (Exception ignored) {
                // 单个特效失败不影响其余
            }
        }
    }

    public static SkillEffect of(SkillEffect... effects) {
        return new CompositeEffect(Arrays.asList(effects));
    }
}
