package com.skillcore.armor;

import com.skillcore.armor.annotation.ArmorSkillInfo;
import com.skillcore.armor.skills.SunfireArmorSkill;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 技能盔甲工厂 — 从配置解析 {@link ArmorSet}，并注册盔甲技能实现。
 * <p>
 * 盔甲技能（{@link ArmorSkill}）由 {@code skill: 类型} 键绑定，
 * 集齐整套后由 {@link ArmorManager} 走工厂创建并派发生命周期钩子。
 */
public final class ArmorFactory {

    /** 默认扫描的盔甲技能包。 */
    public static final String DEFAULT_SKILL_PACKAGE = "com.skillcore.armor.skills";

    /** 盔甲技能类型 key -> 创建器。 */
    private final Map<String, Function<ArmorSet, ArmorSkill>> skillCreators = new ConcurrentHashMap<>();

    /** 盔甲技能类型 key -> 配置键默认值（供缺失键自动补全）。 */
    private static final Map<String, Map<String, Object>> SKILL_DEFAULTS = new ConcurrentHashMap<>();

    static {
        // 内置技能默认键（与技能类里 defaults() 一致）
        SKILL_DEFAULTS.put("SUNFIRE", new SunfireArmorSkill().defaults());
        SKILL_DEFAULTS.put("SUNFIRE_ARMOR", new SunfireArmorSkill().defaults());
        SKILL_DEFAULTS.put("BURN_AURA", new SunfireArmorSkill().defaults());
    }

    public ArmorFactory() {
        registerBuiltins();
    }

    /** 内置盔甲技能（注解扫描之外的兜底）。 */
    private void registerBuiltins() {
        registerSkill("SUNFIRE", s -> new SunfireArmorSkill());
        registerSkill("SUNFIRE_ARMOR", s -> new SunfireArmorSkill());
        registerSkill("BURN_AURA", s -> new SunfireArmorSkill());
    }

    /**
     * 扫描带 {@link ArmorSkillInfo} 注解的盔甲技能类并自动注册。
     *
     * @return 本次扫描注册（或覆盖）的数量
     */
    public int autoRegister(Plugin plugin, String... packages) {
        if (plugin == null) {
            return 0;
        }
        String[] targets = (packages == null || packages.length == 0)
                ? new String[]{DEFAULT_SKILL_PACKAGE}
                : packages;
        int count = 0;
        for (String pkg : targets) {
            for (Class<?> clazz : com.skillcore.weapon.ClassScanner.scan(plugin, pkg)) {
                if (registerAnnotated(plugin, clazz)) {
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Auto-registered " + count + " armor skill class(es) from annotations.");
        }
        return count;
    }

    private boolean registerAnnotated(Plugin plugin, Class<?> clazz) {
        ArmorSkillInfo info = clazz.getAnnotation(ArmorSkillInfo.class);
        if (info == null || !info.enabled()) {
            return false;
        }
        if (!ArmorSkill.class.isAssignableFrom(clazz)) {
            plugin.getLogger().warning("@ArmorSkillInfo class does not implement ArmorSkill: " + clazz.getName());
            return false;
        }
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Function<ArmorSet, ArmorSkill> creator = s -> {
                try {
                    return (ArmorSkill) ctor.newInstance();
                } catch (Exception ex) {
                    throw new IllegalStateException("无法实例化盔甲技能: " + clazz.getName(), ex);
                }
            };
            registerSkill(info.id(), creator);
            // 同步登记配置键默认值（若注解类实现了 defaults）
            try {
                ArmorSkill sample = creator.apply(null);
                SKILL_DEFAULTS.put(info.id().toUpperCase(Locale.ROOT), sample.defaults());
            } catch (Exception ignored) {
            }
            for (String alias : info.aliases()) {
                registerSkill(alias, creator);
            }
            return true;
        } catch (NoSuchMethodException ex) {
            plugin.getLogger().warning("@ArmorSkillInfo class needs a no-arg constructor: " + clazz.getName());
            return false;
        }
    }

    /**
     * 注册盔甲技能实现。
     */
    public void registerSkill(String type, Function<ArmorSet, ArmorSkill> creator) {
        if (type != null && creator != null) {
            skillCreators.put(type.toUpperCase(Locale.ROOT), creator);
        }
    }

    /** 按类型 key 创建盔甲技能（未注册返回 null）。 */
    public ArmorSkill createSkill(String type, ArmorSet set) {
        if (type == null) {
            return null;
        }
        Function<ArmorSet, ArmorSkill> fn = skillCreators.get(type.toUpperCase(Locale.ROOT));
        return fn == null ? null : fn.apply(set);
    }

    /** 某盔甲技能的配置键默认值（供 ConfigManager 补全缺失键）。 */
    public static Map<String, Object> defaultsFor(String type) {
        if (type == null) {
            return Map.of();
        }
        return SKILL_DEFAULTS.getOrDefault(type.toUpperCase(Locale.ROOT), Map.of());
    }

    /** 解析一套盔甲（armor/<id>.yml）。 */
    public ArmorSet parse(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("armor section null: " + id);
        }
        String displayName = section.getString("display-name", id);
        String description = section.getString("description", "");
        String skillType = firstString(section, "skill", "skill-type", "armor-skill");

        ArmorPiece helmet = parsePiece(section.getConfigurationSection("helmet"), ArmorSlot.HELMET, displayName);
        ArmorPiece chestplate = parsePiece(section.getConfigurationSection("chestplate"), ArmorSlot.CHESTPLATE, displayName);
        ArmorPiece leggings = parsePiece(section.getConfigurationSection("leggings"), ArmorSlot.LEGGINGS, displayName);
        ArmorPiece boots = parsePiece(section.getConfigurationSection("boots"), ArmorSlot.BOOTS, displayName);

        ArmorStats stats = ArmorStats.fromConfig(section.getConfigurationSection("set-bonus"));

        return new ArmorSet(id, displayName, description, helmet, chestplate, leggings, boots, stats, skillType);
    }

    private ArmorPiece parsePiece(ConfigurationSection section, ArmorSlot slot, String fallbackName) {
        if (section == null) {
            return new ArmorPiece(slot, fallbackName, java.util.List.of(), null, 0, false, "", Map.of());
        }
        String name = section.getString("display-name", fallbackName);
        var lore = section.getStringList("lore");
        Material material = parseMaterial(section.getString("material"));
        int cmd = section.getInt("custom-model-data", 0);
        boolean unbreakable = section.getBoolean("unbreakable", false);
        String craftEngineModel = firstString(section, "craftengine_model", "craftengine-model");
        if (craftEngineModel == null) craftEngineModel = "";
        Map<String, Integer> enchantments = parseEnchantments(section);
        return new ArmorPiece(slot, name, lore, material, cmd, unbreakable, craftEngineModel, enchantments);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return Material.matchMaterial(raw);
    }

    private static Map<String, Integer> parseEnchantments(ConfigurationSection section) {
        Map<String, Integer> result = new LinkedHashMap<>();
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

    private static String firstString(ConfigurationSection section, String... keys) {
        for (String key : keys) {
            String v = section.getString(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }
}
