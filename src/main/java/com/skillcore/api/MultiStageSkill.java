package com.skillcore.api;

import org.bukkit.entity.Player;

/**
 * Optional multi-stage skill (charge, release, channel).
 */
public interface MultiStageSkill extends ActiveSkill {

    /** Max stages / charges. */
    int getMaxStage();

    /**
     * Advance one stage.
     *
     * @return true if stage was advanced and skill should NOT finish yet
     */
    boolean nextStage(SkillContext context, int currentStage);

    /** Called when the skill finishes (player releases / timeout). */
    void finish(SkillContext context, int finalStage);

    /** Reset stages (player cancelled / died). */
    default void reset(Player player) {
    }
}
