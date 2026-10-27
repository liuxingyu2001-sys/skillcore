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
        String key = type.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return new StrikeSkill();
        Function<SkillWeapon, WeaponSkill> fn = skillCreators.get(key);
        if (fn == null) {
            // 配置里写了不存在的技能 key 时不要静默降级为“普通强击”，否则很难排查
            org.bukkit.Bukkit.getLogger().warning(
                    "未知武器技能类型: " + type + "（武器 " + (weapon == null ? "?" : weapon.id())
                            + "），已回退为 STRIKE，请检查 skills/*.yml 的 right-skill / left-skill");
            return new StrikeSkill();
        }
        return fn.apply(weapon);
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
        Material material = parseMaterial(section.getString("material", "IRON_SWORD"), id);
        List<String> lore = section.getStringList("lore");

        String rightSkill = firstString(section,
                "right-skill", "skill-type", "skill", "right-skill-type");
        String leftSkill = firstString(section, "left-skill", "left-skill-type");

        String rightTriggerRaw = section.getString("right-trigger", section.getString("trigger", "RIGHT_CLICK"));
        String leftTriggerRaw = section.getString("left-trigger", "LEFT_CLICK");

        WeaponStats stats = parseStats(section);

        int cmd = section.getInt("custom-model-data", 0);
        boolean glow = section.getBoolean("glow", false);
        boolean unbreakable = section.getBoolean("unbreakable", false);
        String craftEngineModel = firstString(section, "craftengine_model", "craftengine-model");
        if (craftEngineModel == null) craftEngineModel = "";
        java.util.Map<String, Integer> enchantments = parseEnchantments(section);

        return new SkillWeapon(
                id, displayName, description, material, lore,
                parseTrigger(rightTriggerRaw, id, "right-trigger"),
                parseTrigger(leftTriggerRaw, id, "left-trigger"),
                rightSkill,
                leftSkill,
                stats, cmd, glow, craftEngineModel, unbreakable, enchantments
        );
    }

    /**
     * 解析数值：{@code stats:} 段优先，文件顶层的数值键作为回退。
     * <p>
     * 这里必须合并到一份<b>临时</b>配置里，不能直接往入参的 {@code stats:} 段 {@code set(...)}：
     * 入参是 {@code ConfigManager} 缓存的 {@link org.bukkit.configuration.file.YamlConfiguration}，
     * 直接写会污染缓存（任何后续 {@code save()} 都会把 lore / enchantments 等段对象落盘）。
     */
    private static WeaponStats parseStats(ConfigurationSection root) {
        ConfigurationSection statsSection = root.getConfigurationSection("stats");
        if (statsSection == null) {
            return WeaponStats.fromConfig(root);
        }
        org.bukkit.configuration.MemoryConfiguration merged = new org.bukkit.configuration.MemoryConfiguration();
        for (String key : statsSection.getKeys(true)) {
            merged.set(key, statsSection.get(key));
        }
        for (String key : root.getKeys(false)) {
            if (key.equals("stats") || merged.contains(key)) {
                continue;
            }
            Object value = root.get(key);
            // 只回退标量数值键：段 / 列表（enchantments、lore 等）不能被写进 stats
            if (value == null || value instanceof ConfigurationSection
                    || value instanceof java.util.List || value instanceof java.util.Map) {
                continue;
            }
            merged.set(key, value);
        }
        return WeaponStats.fromConfig(merged);
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

    private static Material parseMaterial(String raw, String weaponId) {
        if (raw == null || raw.isBlank()) return Material.IRON_SWORD;
        Material m = Material.matchMaterial(raw.trim());
        // 必须是「可放进物品栏」的材质：AIR / WATER 等方块状态类材质构造出来的 ItemStack 无效，
        // PDC 写不进去，give 出去玩家也拿不到东西
        if (m == null || !m.isItem() || m.isAir()) {
            org.bukkit.Bukkit.getLogger().warning(
                    "武器 " + weaponId + " 的 material 无效: " + raw + "，已回退为 IRON_SWORD");
            return Material.IRON_SWORD;
        }
        return m;
    }

    private static com.skillcore.weapon.SkillTrigger parseTrigger(String raw, String weaponId, String key) {
        if (raw == null || raw.isBlank()) return com.skillcore.weapon.SkillTrigger.RIGHT_CLICK;
        try {
            return com.skillcore.weapon.SkillTrigger.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (Exception ex) {
            org.bukkit.Bukkit.getLogger().warning(
                    "武器 " + weaponId + " 的 " + key + " 无效: " + raw + "，已回退为 RIGHT_CLICK");
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
