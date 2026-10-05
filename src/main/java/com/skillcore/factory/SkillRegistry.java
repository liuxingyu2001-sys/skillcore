package com.skillcore.factory;

import com.skillcore.api.Skill;
import com.skillcore.api.SkillTrigger;
import com.skillcore.api.SkillType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all available skills.
 */
public final class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /**
     * Register a skill. Replaces existing skill with the same id.
     */
    public void register(Skill skill) {
        if (skill == null || skill.getId() == null) {
            return;
        }
        skills.put(skill.getId().toLowerCase(), skill);
    }

    public void registerAll(Collection<? extends Skill> collection) {
        if (collection == null) {
            return;
        }
        for (Skill skill : collection) {
            register(skill);
        }
    }

    public boolean unregister(String id) {
        return id != null && skills.remove(id.toLowerCase()) != null;
    }

    public Optional<Skill> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(id.toLowerCase()));
    }

    public Skill getOrNull(String id) {
        return get(id).orElse(null);
    }

    public boolean has(String id) {
        return id != null && skills.containsKey(id.toLowerCase());
    }

    public Collection<Skill> all() {
        return List.copyOf(skills.values());
    }

    public List<Skill> byType(SkillType type) {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            if (skill.getType() == type) {
                result.add(skill);
            }
        }
        return result;
    }

    public List<Skill> byTrigger(SkillTrigger trigger) {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            if (skill.getTrigger() == trigger) {
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * Find the first skill bound to a trigger (for simple click binding).
     */
    public Optional<Skill> firstByTrigger(SkillTrigger trigger) {
        return byTrigger(trigger).stream().findFirst();
    }

    public int size() {
        return skills.size();
    }

    public void clear() {
        skills.clear();
    }

    /**
     * Get or create is not needed; just check.
     */
    public Collection<String> ids() {
        return List.copyOf(skills.keySet());
    }
}
