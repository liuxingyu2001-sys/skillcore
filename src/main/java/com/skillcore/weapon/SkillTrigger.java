package com.skillcore.weapon;

/**
 * Input trigger for active skills.
 */
public enum SkillTrigger {
    LEFT_CLICK,
    RIGHT_CLICK,
    SHIFT_LEFT_CLICK,
    SHIFT_RIGHT_CLICK,
    BOTH_CLICK,
    COMMAND,
    /** 双击 Shift（窗口见 config.yml input.double-shift-window-ms）。 */
    DOUBLE_SHIFT,
    /** 按住 Shift 达到阈值时（input.hold-shift-delay-ms）。 */
    HOLD_SHIFT,
    /** 松开 Shift（结束蓄力/防御）。 */
    RELEASE_SHIFT;

    public boolean matches(boolean leftClick, boolean sneak) {
        return switch (this) {
            case LEFT_CLICK -> leftClick && !sneak;
            case RIGHT_CLICK -> !leftClick && !sneak;
            case SHIFT_LEFT_CLICK -> leftClick && sneak;
            case SHIFT_RIGHT_CLICK -> !leftClick && sneak;
            case BOTH_CLICK -> true;
            case COMMAND, DOUBLE_SHIFT, HOLD_SHIFT, RELEASE_SHIFT -> false;
        };
    }

    /** 该触发是否属于 Shift 状态（用于冷却 key 分组）。 */
    public boolean isShift() {
        return this == SHIFT_LEFT_CLICK || this == SHIFT_RIGHT_CLICK
                || this == DOUBLE_SHIFT || this == HOLD_SHIFT || this == RELEASE_SHIFT;
    }
}
