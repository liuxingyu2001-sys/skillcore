package com.skillcore.weapon;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
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

        /**
         * 按点击侧解析技能：左键只认 left-skill，右键只认 right-skill。
         * <p>
         * 不做跨键回退：只配置了 right-skill 的武器，左键不应该再放一次右键技能，
         * 否则同一技能会拥有左右两套独立冷却（可被利用来双倍释放）。
         */
        public WeaponSkill resolve(boolean leftClick) {
            return leftClick ? leftSkill : skill;
        }
    }

    private final Map<String, Entry> weapons = new ConcurrentHashMap<>();

    private static String normalize(String id) {
        return id == null ? null : id.trim().toLowerCase(Locale.ROOT);
    }

    public void register(SkillWeapon weapon, WeaponSkill rightSkill, WeaponSkill leftSkill) {
        String id = weapon == null ? null : normalize(weapon.id());
        if (id == null || id.isEmpty()) return;
        Entry old = weapons.put(id, new Entry(weapon, rightSkill, leftSkill));
        if (old != null) {
            // 覆盖注册（重载 / 重复 id）时旧实例的持久状态（BossBar / 状态表 / 定时任务）必须清理，
            // 否则每次重载都会多泄漏一份
            safeCleanupEntry(old, rightSkill, leftSkill);
        }
    }

    /** 清理被替换掉的旧实例（与新的左右技能是同一个实例时跳过）。 */
    private void safeCleanupEntry(Entry old, WeaponSkill keepRight, WeaponSkill keepLeft) {
        if (old.skill() != keepRight && old.skill() != keepLeft) {
            safeCleanup(old.skill());
        }
        if (old.leftSkill() != old.skill() && old.leftSkill() != keepRight && old.leftSkill() != keepLeft) {
            safeCleanup(old.leftSkill());
        }
    }

    public void register(Entry entry) {
        if (entry != null && entry.weapon() != null) {
            register(entry.weapon(), entry.skill(), entry.leftSkill());
        }
    }

    public boolean unregister(String id) {
        String key = normalize(id);
        if (key == null) return false;
        Entry removed = weapons.remove(key);
        if (removed == null) return false;
        safeCleanupEntry(removed, null, null);
        return true;
    }

    public Optional<Entry> getEntry(String id) {
        String key = normalize(id);
        return key == null ? Optional.empty() : Optional.ofNullable(weapons.get(key));
    }

    public SkillWeapon get(String id) {
        return getEntry(id).map(Entry::weapon).orElse(null);
    }

    public WeaponSkill getSkill(String id, boolean leftClick) {
        return getEntry(id).map(e -> e.resolve(leftClick)).orElse(null);
    }

    public boolean has(String id) {
        String key = normalize(id);
        return key != null && weapons.containsKey(key);
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
            // 左右键可能共用同一个实例，避免重复 cleanup
            if (entry.leftSkill() != entry.skill()) {
                safeCleanup(entry.leftSkill());
            }
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

    /**
     * 清空注册表。
     * <p>
     * 与 {@link #cleanup()} 等价（先清理技能状态再清表）：{@code ConfigManager.loadWeapons}
     * 会直接调用它，不清理的话旧技能实例的定时任务 / BossBar 会泄漏。
     */
    public void clear() {
        cleanup();
    }
}
