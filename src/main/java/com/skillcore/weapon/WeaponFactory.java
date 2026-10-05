package com.skillcore.weapon;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 技能武器工厂：从配置创建 {@link SkillWeapon}，注册自定义 {@link WeaponSkill} 实现。
 * <p>
 * 以后做新技能武器：
 * <ol>
 *   <li>写一个 {@link AbstractWeaponSkill} 子类</li>
 *   <li>{@code factory.registerSkill("BLADE_DASH", BladeDashSkill::new)}</li>
 *   <li>weapons.yml 里加一段配置，数值随意改</li>
 * </ol>
 */
public final class WeaponFactory {

    /** skill-type -> skill instance creator */
    private final Map<String, Function<SkillWeapon, WeaponSkill>> skillCreators = new ConcurrentHashMap<>();

    public WeaponFactory() {
        registerDefaults();
    }

    private void registerDefaults() {
        // 内置通用技能类型
        skillCreators.put("DASH_DAMAGE", w -> new BuiltinDashDamageSkill());
        skillCreators.put("DASH", w -> new BuiltinDashDamageSkill());
        skillCreators.put("BLADE_DASH", w -> new BuiltinBladeDashSkill());
        skillCreators.put("STRIKE", w -> new BuiltinStrikeSkill());
        skillCreators.put("SWEEP", w -> new BuiltinSweepSkill());
        skillCreators.put("CHARGE", w -> new BuiltinChargeSkill());
        skillCreators.put("PERCENT_STRIKE", w -> new BuiltinPercentStrikeSkill());
        skillCreators.put("CONTROL_STRIKE", w -> new BuiltinControlStrikeSkill());
        skillCreators.put("BLINK_BURST", w -> new BuiltinBlinkBurstSkill());
        skillCreators.put("LIFESTEAL_STRIKE", w -> new BuiltinLifestealStrikeSkill());
        skillCreators.put("THORNS", w -> new BuiltinThornsSkill());
    }

    /**
     * 注册自定义武器技能。
     * <pre>
     * factory.registerSkill("BLADE_DASH", BladeDashSkill::new);
     * </pre>
     */
    public void registerSkill(String type, Function<SkillWeapon, WeaponSkill> creator) {
        if (type != null && creator != null) {
            skillCreators.put(type.toUpperCase(Locale.ROOT), creator);
        }
    }

    public boolean hasSkillType(String type) {
        return type != null && skillCreators.containsKey(type.toUpperCase(Locale.ROOT));
    }

    public WeaponSkill createSkill(String type, SkillWeapon weapon) {
        if (type == null) return new BuiltinStrikeSkill();
        Function<SkillWeapon, WeaponSkill> fn = skillCreators.get(type.toUpperCase(Locale.ROOT));
        return fn != null ? fn.apply(weapon) : new BuiltinStrikeSkill();
    }

    // ------------------------------------------------------------------
    // 配置解析
    // ------------------------------------------------------------------

    /**
     * 从配置段创建武器（不含技能实例）。
     * <pre>
     * blade_dash:
     *   display-name: "&6利刃突刺"
     *   material: NETHERITE_SWORD
     *   right-skill: BLADE_DASH
     *   left-skill: STRIKE
     *   right-trigger: RIGHT_CLICK
     *   left-trigger: LEFT_CLICK
     *   custom-model-data: 0
     *   glow: true
     *   lore:
     *     - "&7右键: 向前突刺"
     *   stats:
     *     damage: 15
     *     cooldown: 5
     *     lifesteal: 0.15
     *     dash-speed: 1.6
     * </pre>
     */
    public SkillWeapon parse(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("weapon section null: " + id);
        }
        String displayName = section.getString("display-name", id);
        String description = section.getString("description", "");
        Material material = parseMaterial(section.getString("material", "IRON_SWORD"));
        List<String> lore = section.getStringList("lore");

        String rightSkill = firstString(section,
                "right-skill", "skill-type", "skill", "right-skill-type");
        String leftSkill = firstString(section, "left-skill", "left-skill-type");

        String rightTriggerRaw = section.getString("right-trigger", section.getString("trigger", "RIGHT_CLICK"));
        String leftTriggerRaw = section.getString("left-trigger", "LEFT_CLICK");

        WeaponStats stats = WeaponStats.fromConfig(section.getConfigurationSection("stats"));
        // 允许把 stats 字段平铺在外层
        if (section.getConfigurationSection("stats") == null) {
            stats = WeaponStats.fromConfig(section);
        }

        int cmd = section.getInt("custom-model-data", 0);
        boolean glow = section.getBoolean("glow", false);

        return new SkillWeapon(
                id, displayName, description, material, lore,
                parseTrigger(rightTriggerRaw),
                parseTrigger(leftTriggerRaw),
                rightSkill,
                leftSkill,
                stats, cmd, glow
        );
    }

    public Map<String, SkillWeapon> parseAll(ConfigurationSection root) {
        Map<String, SkillWeapon> map = new HashMap<>();
        if (root == null) return map;
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec != null) {
                map.put(key, parse(key, sec));
            }
        }
        return map;
    }

    private static String firstString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String v = section.getString(key);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) return Material.IRON_SWORD;
        Material m = Material.matchMaterial(raw);
        return m == null ? Material.IRON_SWORD : m;
    }

    private static com.skillcore.api.SkillTrigger parseTrigger(String raw) {
        if (raw == null) return com.skillcore.api.SkillTrigger.RIGHT_CLICK;
        try {
            return com.skillcore.api.SkillTrigger.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (Exception ex) {
            return com.skillcore.api.SkillTrigger.RIGHT_CLICK;
        }
    }

    // ==================================================================
    // 内置技能实现（开箱即用，也可当模板抄）
    // ==================================================================

    /** 通用：突刺伤害 */
    public static class BuiltinDashDamageSkill extends AbstractWeaponSkill {
        public BuiltinDashDamageSkill() { super("DASH_DAMAGE"); }
        @Override public void onRightClick(WeaponContext ctx) {
            dashSlash(ctx);
            ctx.castFx();
        }
    }

    /** 利刃突刺：向前突刺并斩击路径敌人 */
    public static class BuiltinBladeDashSkill extends AbstractWeaponSkill {
        public BuiltinBladeDashSkill() { super("BLADE_DASH"); }
        @Override public void onRightClick(WeaponContext ctx) {
            var hit = dashSlash(ctx);
            for (org.bukkit.entity.LivingEntity e : hit) {
                ctx.healSelfByDamage(ctx.stats().damage());
            }
            ctx.slowTarget();
            ctx.castFx();
        }
    }

    /** 通用：单体强击 */
    public static class BuiltinStrikeSkill extends AbstractWeaponSkill {
        public BuiltinStrikeSkill() { super("STRIKE"); }
        @Override public void onRightClick(WeaponContext ctx) { strike(ctx); }
        @Override public void onLeftClick(WeaponContext ctx) { strike(ctx); }
    }

    /** 通用：横扫 AOE */
    public static class BuiltinSweepSkill extends AbstractWeaponSkill {
        public BuiltinSweepSkill() { super("SWEEP"); }
        @Override public void onRightClick(WeaponContext ctx) {
            sweep(ctx);
            ctx.castFx();
        }
    }

    /** 通用：冲锋突刺 */
    public static class BuiltinChargeSkill extends AbstractWeaponSkill {
        public BuiltinChargeSkill() { super("CHARGE"); }
        @Override public void onRightClick(WeaponContext ctx) {
            chargeStrike(ctx);
        }
    }

    /** 通用：百分比生命打击 */
    public static class BuiltinPercentStrikeSkill extends AbstractWeaponSkill {
        public BuiltinPercentStrikeSkill() { super("PERCENT_STRIKE"); }
        @Override public void onRightClick(WeaponContext ctx) {
            percentStrike(ctx);
        }
    }

    /** 通用：控制打击 */
    public static class BuiltinControlStrikeSkill extends AbstractWeaponSkill {
        public BuiltinControlStrikeSkill() { super("CONTROL_STRIKE"); }
        @Override public void onRightClick(WeaponContext ctx) {
            controlStrike(ctx);
        }
    }

    /** 通用：闪现爆发 */
    public static class BuiltinBlinkBurstSkill extends AbstractWeaponSkill {
        public BuiltinBlinkBurstSkill() { super("BLINK_BURST"); }
        @Override public void onRightClick(WeaponContext ctx) {
            blinkBurst(ctx);
            ctx.castFx();
        }
    }

    /** 通用：吸血强击 */
    public static class BuiltinLifestealStrikeSkill extends AbstractWeaponSkill {
        public BuiltinLifestealStrikeSkill() { super("LIFESTEAL_STRIKE"); }
        @Override public void onRightClick(WeaponContext ctx) {
            double d = strike(ctx);
            ctx.healSelfByDamage(d * 0.5); // 额外吸血
        }
    }

    /** 通用：反伤 */
    public static class BuiltinThornsSkill extends AbstractWeaponSkill {
        public BuiltinThornsSkill() { super("THORNS"); }
        @Override public void onRightClick(WeaponContext ctx) {
            com.skillcore.utils.ReflectDamageUtils.addReflectBuff(
                    ctx.player(), ctx.stats().reflectPercent(), ctx.stats().reflectFlat(), 200);
            ctx.castFx();
        }
        @Override public void onDamaged(WeaponContext ctx, org.bukkit.entity.LivingEntity attacker, double damage) {
            com.skillcore.utils.ReflectDamageUtils.reflect(
                    ctx.player(), attacker, damage, ctx.stats().reflectPercent(), ctx.stats().reflectFlat());
        }
    }
}
