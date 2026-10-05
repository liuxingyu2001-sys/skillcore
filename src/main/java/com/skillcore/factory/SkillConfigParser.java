package com.skillcore.factory;

import com.skillcore.api.Skill;
import com.skillcore.api.SkillTrigger;
import com.skillcore.api.SkillType;
import com.skillcore.model.SkillDefinition;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds {@link SkillDefinition} from YAML configuration.
 * <pre>
 * skills:
 *   fireball:
 *     display-name: "&c火球术"
 *     type: ACTIVE
 *     trigger: RIGHT_CLICK
 *     cooldown: 5.0
 *     mana-cost: 20
 *     icon: FIRE_CHARGE
 *     damage:
 *       base: 8.0
 *       scaling: 1.2
 *       critical-chance: 0.1
 *       critical-multiplier: 1.5
 *       armor-penetration: 0.1
 * </pre>
 */
public final class SkillConfigParser {

    private SkillConfigParser() {
    }

    /**
     * Parse a single skill section (id already known).
     */
    public static SkillDefinition parse(String id, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Skill section is null for id=" + id);
        }
        String displayName = section.getString("display-name", id);
        String description = section.getString("description", "");
        SkillType type = parseType(section.getString("type", "ACTIVE"));
        SkillTrigger trigger = parseTrigger(section.getString("trigger", "RIGHT_CLICK"));
        double cooldown = section.getDouble("cooldown", 1.0);
        double manaCost = section.getDouble("mana-cost", section.getDouble("mana", 0.0));
        String permission = section.getString("permission", null);
        Material icon = parseMaterial(section.getString("icon", "PAPER"));
        List<String> lore = section.getStringList("lore");
        SkillDefinition.DamageProfile damage = parseDamage(section.getConfigurationSection("damage"));
        Map<String, Object> extras = parseExtras(section);

        return new SkillDefinition(
                id, displayName, description, type, trigger,
                cooldown, manaCost, permission, icon, lore, damage, extras
        );
    }

    /**
     * Parse all skills under a root section.
     */
    public static Map<String, SkillDefinition> parseAll(ConfigurationSection root) {
        Map<String, SkillDefinition> result = new HashMap<>();
        if (root == null) {
            return result;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section != null) {
                result.put(key, parse(key, section));
            }
        }
        return result;
    }

    public static SkillDefinition.DamageProfile parseDamage(ConfigurationSection section) {
        if (section == null) {
            return SkillDefinition.DamageProfile.EMPTY;
        }
        return new SkillDefinition.DamageProfile(
                section.getDouble("base", 0),
                section.getDouble("scaling", 0),
                section.getDouble("critical-chance", section.getDouble("crit-chance", 0.1)),
                section.getDouble("critical-multiplier", section.getDouble("crit-multiplier", 1.5)),
                section.getDouble("armor-penetration", section.getDouble("penetration", 0)),
                section.getDouble("percent-max-health", 0),
                section.getDouble("percent-current-health", 0),
                section.getDouble("percent-missing-health", 0),
                section.getDouble("max-percent-damage", 0)
        );
    }

    public static SkillType parseType(String raw) {
        if (raw == null) {
            return SkillType.ACTIVE;
        }
        try {
            return SkillType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return SkillType.ACTIVE;
        }
    }

    public static SkillTrigger parseTrigger(String raw) {
        if (raw == null) {
            return SkillTrigger.RIGHT_CLICK;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        // aliases
        switch (normalized) {
            case "LEFT", "LCLICK", "LEFT_CLICK", "LEFTCLICK":
                return SkillTrigger.LEFT_CLICK;
            case "RIGHT", "RCLICK", "RIGHT_CLICK", "RIGHTCLICK":
                return SkillTrigger.RIGHT_CLICK;
            case "SHIFT_LEFT", "SNEAK_LEFT", "SHIFT_LEFT_CLICK":
                return SkillTrigger.SHIFT_LEFT_CLICK;
            case "SHIFT_RIGHT", "SNEAK_RIGHT", "SHIFT_RIGHT_CLICK":
                return SkillTrigger.SHIFT_RIGHT_CLICK;
            case "BOTH", "ANY", "BOTH_CLICK":
                return SkillTrigger.BOTH_CLICK;
            case "CMD", "COMMAND":
                return SkillTrigger.COMMAND;
            default:
                try {
                    return SkillTrigger.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    return SkillTrigger.RIGHT_CLICK;
                }
        }
    }

    public static Material parseMaterial(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Material.PAPER;
        }
        Material material = Material.matchMaterial(raw);
        return material == null ? Material.PAPER : material;
    }

    /**
     * Copy unknown keys into extras map for custom skill types.
     */
    public static Map<String, Object> parseExtras(ConfigurationSection section) {
        Map<String, Object> extras = new HashMap<>();
        if (section == null) {
            return extras;
        }
        List<String> known = List.of(
                "display-name", "description", "type", "trigger", "cooldown",
                "mana-cost", "mana", "permission", "icon", "lore", "damage"
        );
        for (String key : section.getKeys(false)) {
            if (known.contains(key)) {
                continue;
            }
            extras.put(key, section.get(key));
        }
        return extras;
    }

    /**
     * Quick builder-based definition (for programmatic registration).
     */
    public static SkillDefinition simple(
            String id,
            String displayName,
            SkillType type,
            SkillTrigger trigger,
            double cooldown,
            double manaCost
    ) {
        return new SkillDefinition(
                id, displayName, "", type, trigger, cooldown, manaCost,
                null, Material.PAPER, List.of(), SkillDefinition.DamageProfile.EMPTY, Map.of()
        );
    }
}
