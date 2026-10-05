package com.skillcore.model;

import com.skillcore.api.SkillTrigger;
import com.skillcore.api.SkillType;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Immutable skill definition loaded from configuration.
 * Used by {@link com.skillcore.factory.SkillFactory} to build skill instances.
 */
public final class SkillDefinition {

    private final String id;
    private final String displayName;
    private final String description;
    private final SkillType type;
    private final SkillTrigger trigger;
    private final double cooldown;
    private final double manaCost;
    private final String permission;
    private final Material icon;
    private final List<String> lore;
    private final DamageProfile damage;
    private final Map<String, Object> extras;

    public SkillDefinition(
            String id,
            String displayName,
            String description,
            SkillType type,
            SkillTrigger trigger,
            double cooldown,
            double manaCost,
            String permission,
            Material icon,
            List<String> lore,
            DamageProfile damage,
            Map<String, Object> extras
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.trigger = trigger;
        this.cooldown = cooldown;
        this.manaCost = manaCost;
        this.permission = permission;
        this.icon = icon;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.damage = damage == null ? DamageProfile.EMPTY : damage;
        this.extras = extras == null ? Map.of() : Map.copyOf(extras);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public SkillType getType() {
        return type;
    }

    public SkillTrigger getTrigger() {
        return trigger;
    }

    public double getCooldown() {
        return cooldown;
    }

    public double getManaCost() {
        return manaCost;
    }

    public String getPermission() {
        return permission;
    }

    public Material getIcon() {
        return icon;
    }

    public List<String> getLore() {
        return lore;
    }

    public DamageProfile getDamage() {
        return damage;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key, Class<T> type) {
        Object value = extras.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    public double getExtraDouble(String key, double def) {
        Object value = extras.get(key);
        return value instanceof Number number ? number.doubleValue() : def;
    }

    public String getExtraString(String key, String def) {
        Object value = extras.get(key);
        return value instanceof String s ? s : def;
    }

    public boolean getExtraBoolean(String key, boolean def) {
        Object value = extras.get(key);
        return value instanceof Boolean b ? b : def;
    }

    /**
     * Damage profile embedded in skill definition.
     */
    public record DamageProfile(
            double base,
            double scaling,
            double criticalChance,
            double criticalMultiplier,
            double armorPenetration,
            double percentMaxHealth,
            double percentCurrentHealth,
            double percentMissingHealth,
            double maxPercentDamage
    ) {
        public static final DamageProfile EMPTY = new DamageProfile(0, 0, 0.1, 1.5, 0, 0, 0, 0, 0);
    }
}
