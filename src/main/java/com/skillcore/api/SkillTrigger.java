package com.skillcore.api;

/**
 * Input trigger for active skills.
 */
public enum SkillTrigger {
    LEFT_CLICK,
    RIGHT_CLICK,
    SHIFT_LEFT_CLICK,
    SHIFT_RIGHT_CLICK,
    BOTH_CLICK,
    COMMAND;

    public boolean matches(boolean leftClick, boolean sneak) {
        return switch (this) {
            case LEFT_CLICK -> leftClick && !sneak;
            case RIGHT_CLICK -> !leftClick && !sneak;
            case SHIFT_LEFT_CLICK -> leftClick && sneak;
            case SHIFT_RIGHT_CLICK -> !leftClick && sneak;
            case BOTH_CLICK -> true;
            case COMMAND -> false;
        };
    }
}
