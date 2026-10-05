package com.skillcore.weapon;

import com.skillcore.api.SkillTrigger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 一把技能武器的完整定义：外观 + 数值 + 技能绑定。
 * 同一把武器 id 在服务器内唯一，数值可按配置/强化变化。
 */
public final class SkillWeapon {

    private final String id;
    private final String displayName;
    private final String description;
    private final Material material;
    private final List<String> lore;
    private final SkillTrigger trigger;
    private final SkillTrigger leftTrigger;
    private final SkillTrigger rightTrigger;
    private final String skillType;
    private final String leftSkillType;
    private final String rightSkillType;
    private final WeaponStats stats;
    private final int customModelData;
    private final boolean glow;
    private final List<String> defaultLoreFormat;

    public SkillWeapon(
            String id,
            String displayName,
            String description,
            Material material,
            List<String> lore,
            SkillTrigger rightTrigger,
            SkillTrigger leftTrigger,
            String rightSkillType,
            String leftSkillType,
            WeaponStats stats,
            int customModelData,
            boolean glow
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.material = material == null ? Material.IRON_SWORD : material;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.rightTrigger = rightTrigger == null ? SkillTrigger.RIGHT_CLICK : rightTrigger;
        this.leftTrigger = leftTrigger == null ? SkillTrigger.LEFT_CLICK : leftTrigger;
        this.trigger = this.rightTrigger;
        this.rightSkillType = rightSkillType;
        this.leftSkillType = leftSkillType;
        this.skillType = rightSkillType;
        this.stats = stats == null ? new WeaponStats() : stats;
        this.customModelData = customModelData;
        this.glow = glow;
        this.defaultLoreFormat = List.of();
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public Material material() { return material; }
    public List<String> lore() { return lore; }

    /** 主技能触发（右键） */
    public SkillTrigger trigger() { return rightTrigger; }
    public SkillTrigger rightTrigger() { return rightTrigger; }
    public SkillTrigger leftTrigger() { return leftTrigger; }

    /** 右键技能类型 key（工厂注册用） */
    public String skillType() { return rightSkillType; }
    public String rightSkillType() { return rightSkillType; }
    public String leftSkillType() { return leftSkillType; }

    public WeaponStats stats() { return stats; }
    public int customModelData() { return customModelData; }
    public boolean glow() { return glow; }

    /**
     * 生成武器物品（带 PDC 标记 weapon_id）。
     */
    public ItemStack toItemStack() {
        return WeaponItems.create(this);
    }

    /**
     * 用当前数值副本（强化/附魔改数值时不污染定义）。
     */
    public SkillWeapon withStats(WeaponStats newStats) {
        return new SkillWeapon(
                id, displayName, description, material, lore,
                rightTrigger, leftTrigger, rightSkillType, leftSkillType,
                newStats, customModelData, glow
        );
    }

    @Override
    public String toString() {
        return "SkillWeapon{" + id + ", dmg=" + stats.damage() + ", cd=" + stats.cooldown() + "}";
    }
}
