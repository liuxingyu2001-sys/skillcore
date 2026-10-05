package com.skillcore.api;

/**
 * Skill category.
 */
public enum SkillType {
    /** Triggered by player input (click). */
    ACTIVE,
    /** Triggered automatically by game events. */
    PASSIVE,
    /** High power / long cooldown. */
    ULTIMATE,
    /** Toggleable on/off skill. */
    TOGGLE
}
