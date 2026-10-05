package com.skillcore.weapon;

import com.skillcore.weapon.annotation.WeaponSkillInfo;
import com.skillcore.weapon.skills.BladeDashSkill;
import com.skillcore.weapon.skills.BlinkBurstSkill;
import com.skillcore.weapon.skills.ChargeSkill;
import com.skillcore.weapon.skills.ControlStrikeSkill;
import com.skillcore.weapon.skills.DashDamageSkill;
import com.skillcore.weapon.skills.LifestealStrikeSkill;
import com.skillcore.weapon.skills.PercentStrikeSkill;
import com.skillcore.weapon.skills.StrikeSkill;
import com.skillcore.weapon.skills.SunfireBladeSkill;
import com.skillcore.weapon.skills.SweepSkill;
import com.skillcore.weapon.skills.ThornsSkill;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 技能武器工厂：从配置创建 {@link SkillWeapon}，注册 {@link WeaponSkill} 实现。
 * <p>
 * 新增武器技能（注解自动注册）：
 * <ol>
 *   <li>在 {@code com.skillcore.weapon.skills} 下写一个 {@link AbstractWeaponSkill} 子类</li>
 *   <li>标注 {@code @WeaponSkillInfo(id = "MY_TYPE")}</li>
 *   <li>skills/ 目录下新建一个 yml，{@code right-skill: MY_TYPE}，只写本技能用到的数值</li>
 * </ol>
 * {@link #autoRegister} 在启动时扫描该包，无需手动注册。
 */
public final class WeaponFactory {

    /** 默认扫描的武器技能包。 */
    public static final String DEFAULT_SKILL_PACKAGE = "com.skillcore.weapon.skills";

    /** skill-type -> skill instance creator */
    private final Map<String, Function<SkillWeapon, WeaponSkill>> skillCreators = new ConcurrentHashMap<>();

    public WeaponFactory() {
        registerBuiltins();
    }

    /**
     * 显式注册内置技能，保证任何环境下都可用（扫描失败也有兜底）。
     */
    private void registerBuiltins() {
        registerSkill("DASH_DAMAGE", w -> new DashDamageSkill());
        registerSkill("DASH", w -> new DashDamageSkill());
        registerSkill("BLADE_DASH", w -> new BladeDashSkill());
        registerSkill("STRIKE", w -> new StrikeSkill());
        registerSkill("SWEEP", w -> new SweepSkill());
        registerSkill("CHARGE", w -> new ChargeSkill());
        registerSkill("PERCENT_STRIKE", w -> new PercentStrikeSkill());
        registerSkill("CONTROL_STRIKE", w -> new ControlStrikeSkill());
        registerSkill("BLINK_BURST", w -> new BlinkBurstSkill());
        registerSkill("LIFESTEAL_STRIKE", w -> new LifestealStrikeSkill());
        registerSkill("THORNS", w -> new ThornsSkill());
        registerSkill("SUNFIRE_BLADE", w -> new SunfireBladeSkill());
        registerSkill("SUNFIRE_SWORD", w -> new SunfireBladeSkill());
    }

    /**
     * 扫描带 {@link WeaponSkillInfo} 注解的技能类并自动注册。
     * <p>
     * 支持开发期 class 目录与打包后 jar 两种形态。
     *
     * @return 本次扫描注册（或覆盖）的数量
     */
    public int autoRegister(Plugin plugin, String... packages) {
        if (plugin == null) return 0;
        String[] targets = (packages == null || packages.length == 0)
                ? new String[]{DEFAULT_SKILL_PACKAGE}
                : packages;
        int count = 0;
        for (String pkg : targets) {
            for (Class<?> clazz : ClassScanner.scan(plugin, pkg)) {
                if (registerAnnotated(plugin, clazz)) {
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Auto-registered " + count + " weapon skill class(es) from annotations.");
        }
        return count;
    }

    private boolean registerAnnotated(Plugin plugin, Class<?> clazz) {
        WeaponSkillInfo info = clazz.getAnnotation(WeaponSkillInfo.class);
        if (info == null || !info.enabled()) {
            return false;
        }
        if (!WeaponSkill.class.isAssignableFrom(clazz)) {
            plugin.getLogger().warning("@WeaponSkillInfo class does not implement WeaponSkill: " + clazz.getName());
            return false;
        }
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            registerSkill(info.id(), w -> {
                try {
                    return (WeaponSkill) ctor.newInstance();
                } catch (Exception ex) {
                    throw new IllegalStateException("无法实例化武器技能: " + clazz.getName(), ex);
                }
            });
            for (String alias : info.aliases()) {
                registerSkill(alias, skillCreators.get(info.id().toUpperCase(Locale.ROOT)));
            }
            return true;
        } catch (NoSuchMethodException ex) {
            plugin.getLogger().warning("@WeaponSkillInfo class needs a no-arg constructor: " + clazz.getName());
            return false;
        }
    }

    /**
     * 注册自定义武器技能。
     * <pre>
     * factory.registerSkill("BLADE_DASH", w -> new BladeDashSkill());
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

    public java.util.Set<String> skillTypes() {
        return java.util.Set.copyOf(skillCreators.keySet());
    }

    public WeaponSkill createSkill(String type, SkillWeapon weapon) {
        if (type == null) return new StrikeSkill();
        Function<SkillWeapon, WeaponSkill> fn = skillCreators.get(type.toUpperCase(Locale.ROOT));
        return fn != null ? fn.apply(weapon) : new StrikeSkill();
    }

    // ------------------------------------------------------------------
    // 配置解析
    // ------------------------------------------------------------------

    /**
     * 从配置段创建武器（不含技能实例）。
     * <pre>
     * # skills/blade_dash.yml
     * display-name: "&amp;6利刃突刺"
     * material: NETHERITE_SWORD
     * right-skill: BLADE_DASH
     * left-skill: STRIKE
     * glow: true
     * lore:
     *   - "&amp;7右键: 向前突刺"
     * stats:
     *   damage: 15
     *   cooldown: 5
     *   lifesteal: 0.15
     *   dash-speed: 1.6
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

        WeaponStats stats;
        // 数值既可写在 stats: 段，也可平铺在文件顶层（stats: 段优先）
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection == null) {
            stats = WeaponStats.fromConfig(section);
        } else {
            mergeTopLevelStats(section, statsSection);
            stats = WeaponStats.fromConfig(statsSection);
        }

        int cmd = section.getInt("custom-model-data", 0);
        boolean glow = section.getBoolean("glow", false);
        boolean unbreakable = section.getBoolean("unbreakable", false);
        String craftEngineModel = firstString(section, "craftengine_model", "craftengine-model");
        if (craftEngineModel == null) craftEngineModel = "";
        java.util.Map<String, Integer> enchantments = parseEnchantments(section);

        return new SkillWeapon(
                id, displayName, description, material, lore,
                parseTrigger(rightTriggerRaw),
                parseTrigger(leftTriggerRaw),
                rightSkill,
                leftSkill,
                stats, cmd, glow, craftEngineModel, unbreakable, enchantments
        );
    }

    /**
     * 把文件顶层中未在 {@code stats:} 段出现的键补进 stats（顶层作为回退），
     * 这样 {@code cooldown} 等数值写在 stats 内或文件顶层都能生效，避免“设了没反应”。
     */
    private static void mergeTopLevelStats(ConfigurationSection root, ConfigurationSection stats) {
        for (String key : root.getKeys(false)) {
            if (key.equals("stats") || stats.contains(key)) {
                continue;
            }
            Object value = root.get(key);
            if (value != null) {
                stats.set(key, value);
            }
        }
    }

    /**
     * 解析 enchantments: 段，键为附魔 key（如 wind_burst / sharpness / unbreaking），值为等级。
     */
    private static java.util.Map<String, Integer> parseEnchantments(ConfigurationSection section) {
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection == null) {
            return result;
        }
        for (String key : enchantSection.getKeys(false)) {
            int level = enchantSection.getInt(key, 0);
            if (level > 0) {
                result.put(key.toLowerCase(Locale.ROOT), level);
            }
        }
        return result;
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) return Material.IRON_SWORD;
        Material m = Material.matchMaterial(raw);
        return m == null ? Material.IRON_SWORD : m;
    }

    private static com.skillcore.weapon.SkillTrigger parseTrigger(String raw) {
        if (raw == null) return com.skillcore.weapon.SkillTrigger.RIGHT_CLICK;
        try {
            return com.skillcore.weapon.SkillTrigger.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (Exception ex) {
            return com.skillcore.weapon.SkillTrigger.RIGHT_CLICK;
        }
    }

    /** 兼容旧调用：是否存在某技能实现。 */
    public Optional<Function<SkillWeapon, WeaponSkill>> creator(String type) {
        return type == null ? Optional.empty()
                : Optional.ofNullable(skillCreators.get(type.toUpperCase(Locale.ROOT)));
    }

    private static String firstString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String v = section.getString(key);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
