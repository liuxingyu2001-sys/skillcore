package com.skillcore.api;

import org.bukkit.event.Event;

/**
 * Passive skill triggered automatically by Bukkit events.
 */
public interface PassiveSkill extends Skill {

    @Override
    default SkillType getType() {
        return SkillType.PASSIVE;
    }

    /**
     * Handle a Bukkit event. Implementations should filter event type themselves
     * or rely on {@link #getHandledEvent()}.
     */
    void onEvent(Event event, SkillContext context);

    /**
     * Optional: the exact event class this passive cares about.
     * Null = accept all and filter inside {@link #onEvent}.
     */
    default Class<? extends Event> getHandledEvent() {
        return null;
    }
}
