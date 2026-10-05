package com.skillcore.api;

/**
 * Core skill contract. Every skill (active / passive) implements this.
 */
public interface Skill {

    /** Unique id used in config / commands. */
    String getId();

    /** Display name (color codes allowed). */
    String getDisplayName();

    SkillType getType();

    /** How this skill is triggered. */
    SkillTrigger getTrigger();

    /** Cooldown in seconds. */
    double getCooldown();

    /** Mana / energy cost. 0 = free. */
    double getManaCost();

    /** Permission node, null = no extra permission. */
    String getPermission();

    /** Whether the caster is allowed to use this skill right now. */
    boolean canCast(SkillContext context);

    /**
     * Execute the skill.
     *
     * @return result describing success / failure / cancelled
     */
    SkillResult cast(SkillContext context);

    default String getDescription() {
        return "";
    }
}
