package com.skillcore.weapon;

import com.skillcore.utils.MathUtils;

/**
 * 武器 Lore 变量替换 — 配置里写占位符，生成/更新物品时自动填入当前数值。
 * <pre>
 * lore:
 *   - "&7伤害: &f{damage}"
 *   - "&7冷却: &f{cooldown}s"
 *   - "&7吸血: &f{lifesteal}%"
 * </pre>
 */
public final class WeaponLore {

    private WeaponLore() {
    }

    /**
     * 替换 lore 中的全部变量。
     */
    public static String apply(String line, SkillWeapon weapon) {
        if (line == null || weapon == null) return line;
        WeaponStats s = weapon.stats();
        return line
                .replace("{id}", weapon.id())
                .replace("{display}", weapon.displayName() == null ? weapon.id() : weapon.displayName())

                // 基础
                .replace("{damage}", fmt(s.damage()))
                .replace("{scaling}", fmt(s.damageScaling()))
                .replace("{crit}", fmt(s.criticalChance() * 100))
                .replace("{crit-chance}", fmt(s.criticalChance() * 100))
                .replace("{crit-multi}", fmt(s.criticalMultiplier()))
                .replace("{pen}", fmt(s.armorPenetration() * 100))
                .replace("{armor-penetration}", fmt(s.armorPenetration() * 100))
                .replace("{attack-speed}", fmt(s.attackSpeed()))

                // 百分比
                .replace("{percent-max}", fmt(s.percentMaxHealth() * 100))
                .replace("{percent-current}", fmt(s.percentCurrentHealth() * 100))
                .replace("{percent-missing}", fmt(s.percentMissingHealth() * 100))

                // 吸血 / 反伤
                .replace("{lifesteal}", fmt(s.lifesteal() * 100))
                .replace("{reflect}", fmt(s.reflectPercent() * 100))
                .replace("{reflect-flat}", fmt(s.reflectFlat()))

                // 冷却 / 消耗
                .replace("{cooldown}", fmt(s.cooldown()))
                .replace("{cd}", fmt(s.cooldown()))

                // 位移
                .replace("{dash-speed}", fmt(s.dashSpeed()))
                .replace("{dash-distance}", fmt(s.dashDistance()))
                .replace("{blink}", fmt(s.blinkDistance()))
                .replace("{knockback}", fmt(s.knockbackStrength()))

                // 范围
                .replace("{range}", fmt(s.range()))
                .replace("{aim-range}", fmt(s.aimRange()))
                .replace("{aoe}", fmt(s.aoeRadius()))
                .replace("{aoe-radius}", fmt(s.aoeRadius()))
                .replace("{aoe-ratio}", fmt(s.aoeDamageRatio() * 100))

                // 控制
                .replace("{slow-ticks}", String.valueOf(s.slowDurationTicks()))
                .replace("{stun-ticks}", String.valueOf(s.stunDurationTicks()))
                .replace("{root-ticks}", String.valueOf(s.rootDurationTicks()))

                // 防御 / 回复
                .replace("{heal}", fmt(s.healAmount()))
                .replace("{heal-percent}", fmt(s.healPercentOfMax() * 100))
                .replace("{shield}", fmt(s.shieldAmount()))
                .replace("{dr}", fmt(s.damageReduction() * 100))

                // 多段
                .replace("{hits}", String.valueOf(s.hitCount()))
                .replace("{max-targets}", String.valueOf(s.maxTargets()));
    }

    /**
     * 批量替换整份 lore。
     */
    public static java.util.List<String> applyAll(java.util.List<String> lore, SkillWeapon weapon) {
        if (lore == null || lore.isEmpty()) return java.util.List.of();
        java.util.List<String> out = new java.util.ArrayList<>(lore.size());
        for (String line : lore) {
            out.add(apply(line, weapon));
        }
        return out;
    }

    private static String fmt(double v) {
        // 整数显示 15，小数显示 1.5
        if (Math.abs(v - Math.rint(v)) < 1.0e-6 && Math.abs(v) < 1.0e6) {
            return String.valueOf((long) Math.rint(v));
        }
        return MathUtils.format1(v);
    }

    /**
     * 默认 lore（配置未写 lore 时使用），展示主要数值。
     */
    public static java.util.List<String> defaultLore(SkillWeapon weapon) {
        WeaponStats s = weapon.stats();
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("&7伤害: &f{damage} &8| &7暴击: &f{crit}%");
        lore.add("&7穿透: &f{pen}% &8| &7吸血: &f{lifesteal}%");
        lore.add("&7冷却: &f{cooldown}s &8| &7范围: &f{range}");
        if (s.aoeRadius() > 0) {
            lore.add("&7AOE: &f{aoe} &8| &7溅射: &f{aoe-ratio}%");
        }
        if (s.dashSpeed() > 0 && s.dashDistance() > 0) {
            lore.add("&7位移: &f{dash-distance}格");
        }
        return applyAll(lore, weapon);
    }
}
