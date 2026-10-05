package com.skillcore.api;

/**
 * Result of a skill cast.
 */
public enum SkillResult {
    SUCCESS,
    /** Failed preconditions (cooldown, mana, permission, range...). */
    FAILED,
    /** Explicitly cancelled by event / hook. */
    CANCELLED,
    /** Skill is on cooldown. */
    ON_COOLDOWN,
    /** Not enough mana / resource. */
    NO_MANA,
    /** No valid target. */
    NO_TARGET,
    /** Permission denied. */
    NO_PERMISSION;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
