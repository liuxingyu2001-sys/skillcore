package com.skillcore.weapon;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武器注册表：id -> SkillWeapon + 绑定的 WeaponSkill 实例。
 */
public final class WeaponRegistry {

    public static final class Entry {
        private final SkillWeapon weapon;
        private final WeaponSkill skill;
        private final WeaponSkill leftSkill;

        public Entry(SkillWeapon weapon, WeaponSkill skill, WeaponSkill leftSkill) {
            this.weapon = weapon;
            this.skill = skill;
            this.leftSkill = leftSkill;
        }

        public SkillWeapon weapon() { return weapon; }
        /** 右键技能 */
        public WeaponSkill skill() { return skill; }
        public WeaponSkill leftSkill() { return leftSkill; }

        public WeaponSkill resolve(boolean leftClick) {
            if (leftClick) {
                return leftSkill != null ? leftSkill : skill;
            }
            return skill != null ? skill : leftSkill;
        }
    }

    private final Map<String, Entry> weapons = new ConcurrentHashMap<>();

    public void register(SkillWeapon weapon, WeaponSkill rightSkill, WeaponSkill leftSkill) {
        if (weapon == null || weapon.id() == null) return;
        weapons.put(weapon.id().toLowerCase(), new Entry(weapon, rightSkill, leftSkill));
    }

    public void register(Entry entry) {
        if (entry != null && entry.weapon() != null) {
            register(entry.weapon(), entry.skill(), entry.leftSkill());
        }
    }

    public boolean unregister(String id) {
        return id != null && weapons.remove(id.toLowerCase()) != null;
    }

    public Optional<Entry> getEntry(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(weapons.get(id.toLowerCase()));
    }

    public SkillWeapon get(String id) {
        return getEntry(id).map(Entry::weapon).orElse(null);
    }

    public WeaponSkill getSkill(String id, boolean leftClick) {
        return getEntry(id).map(e -> e.resolve(leftClick)).orElse(null);
    }

    public boolean has(String id) {
        return id != null && weapons.containsKey(id.toLowerCase());
    }

    public Collection<Entry> all() {
        return List.copyOf(weapons.values());
    }

    public Collection<String> ids() {
        return List.copyOf(weapons.keySet());
    }

    public int size() {
        return weapons.size();
    }

    /**
     * 清理所有已注册技能持有的状态（插件禁用 / 重载前调用）。
     */
    public void cleanup() {
        for (Entry entry : weapons.values()) {
            safeCleanup(entry.skill());
            safeCleanup(entry.leftSkill());
        }
        weapons.clear();
    }

    private void safeCleanup(WeaponSkill skill) {
        if (skill == null) return;
        try {
            skill.cleanup();
        } catch (Exception ex) {
            org.bukkit.Bukkit.getLogger().warning(
                    "Failed to cleanup weapon skill " + skill.getClass().getName() + ": " + ex);
        }
    }

    public void clear() {
        weapons.clear();
    }
}
