package com.skillcore.api;

/**
 * Active skill triggered by player input (click / command).
 */
public interface ActiveSkill extends Skill {

    @Override
    default SkillType getType() {
        return SkillType.ACTIVE;
    }

    /**
     * Called before cast to validate range / line-of-sight etc.
     * Return {@link SkillResult#SUCCESS} to continue.
     */
    default SkillResult validate(SkillContext context) {
        return SkillResult.SUCCESS;
    }

    /**
     * Called after a successful cast (for cooldown messages, particles on caster...).
     */
    default void onSuccess(SkillContext context) {
    }

    /**
     * Called when cast fails.
     */
    default void onFail(SkillContext context, SkillResult result) {
    }
}
