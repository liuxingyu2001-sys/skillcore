package com.skillcore.utils;

import com.skillcore.SkillCorePlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;

/**
 * 技能伤害统一出口 — 给目标打上「技能伤害」标记后再造成伤害，
 * 让其他监听器（普攻、反伤、吸血等）能区分技能伤害与普通攻击。
 * <p>
 * 新的玩家来源技能伤害必须走 {@link #damage}，不要直接调用
 * {@link DamageUtils#damage(LivingEntity, double, LivingEntity)}。
 */
public final class SkillDamageUtils {

    private static final String META_SKILL = "skillcore_skill_damage";
    private static final String META_SOURCE = "skillcore_skill_source";
    /** 标记视为有效的窗口（毫秒）。 */
    private static final long TTL_MS = 120L;

    private SkillDamageUtils() {
    }

    /**
     * 造成一次技能伤害并标记来源。
     *
     * @return 实际造成的伤害数值（0 表示被拦截）
     */
    public static double damage(LivingEntity target, double amount, LivingEntity source, String skill) {
        if (target == null || amount <= 0 || !DamageUtils.isAlive(target)) {
            return 0.0;
        }
        if (target instanceof org.bukkit.entity.Player player && DamageUtils.isInvulnerable(player)) {
            return 0.0;
        }
        markSkillDamage(target, skill);
        if (source != null) {
            DamageUtils.damage(target, amount, source);
        } else {
            DamageUtils.damage(target, amount);
        }
        return amount;
    }

    public static double damage(LivingEntity target, double amount, LivingEntity source) {
        return damage(target, amount, source, null);
    }

    /** 打上技能伤害标记。 */
    public static void markSkillDamage(LivingEntity target, String skill) {
        if (target == null) return;
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null) return;
        target.setMetadata(META_SKILL, new FixedMetadataValue(plugin, System.currentTimeMillis()));
        if (skill != null && !skill.isEmpty()) {
            target.setMetadata(META_SOURCE, new FixedMetadataValue(plugin, skill));
        }
    }

    /** 是否处于一次技能伤害窗口内。 */
    public static boolean isSkillDamage(LivingEntity target) {
        if (target == null || !target.hasMetadata(META_SKILL)) return false;
        var values = target.getMetadata(META_SKILL);
        if (values.isEmpty()) return false;
        Object value = values.get(0).value();
        if (value instanceof Number n) {
            return System.currentTimeMillis() - n.longValue() <= TTL_MS;
        }
        return false;
    }

    /** 技能来源 id，无则返回 null。 */
    public static String skillSource(LivingEntity target) {
        if (target == null || !target.hasMetadata(META_SOURCE)) return null;
        var values = target.getMetadata(META_SOURCE);
        return values.isEmpty() ? null : String.valueOf(values.get(0).value());
    }

    /** 清除技能伤害标记。 */
    public static void clearSkillDamage(LivingEntity target) {
        if (target == null) return;
        target.removeMetadata(META_SKILL, SkillCorePlugin.getInstance());
        target.removeMetadata(META_SOURCE, SkillCorePlugin.getInstance());
    }
}
