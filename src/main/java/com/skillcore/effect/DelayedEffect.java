package com.skillcore.effect;

import com.skillcore.SkillCorePlugin;

/**
 * 延迟特效 — 在指定 tick 后播放子特效。
 */
public final class DelayedEffect implements SkillEffect {

    private final SkillEffect child;
    private final long delayTicks;

    public DelayedEffect(SkillEffect child, long delayTicks) {
        this.child = child;
        this.delayTicks = Math.max(0, delayTicks);
    }

    @Override
    public void play(EffectContext context) {
        if (child == null) return;
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null || delayTicks <= 0) {
            child.play(context);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                child.play(context);
            } catch (Exception ignored) {
            }
        }, delayTicks);
    }
}
