package com.skillcore.armor;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * 套装中的一件盔甲（头盔 / 胸甲 / 护腿 / 靴子）的外观定义。
 */
public final class ArmorPiece {

    private final ArmorSlot slot;
    private final String displayName;
    private final List<String> lore;
    private final Material material;
    private final int customModelData;
    private final boolean unbreakable;
    private final String craftEngineModel;
    private final Map<String, Integer> enchantments;

    public ArmorPiece(ArmorSlot slot, String displayName, List<String> lore, Material material,
                      int customModelData, boolean unbreakable, String craftEngineModel,
                      Map<String, Integer> enchantments) {
        this.slot = slot;
        this.displayName = displayName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.material = material == null ? defaultMaterial(slot) : material;
        this.customModelData = customModelData;
        this.unbreakable = unbreakable;
        this.craftEngineModel = craftEngineModel == null ? "" : craftEngineModel;
        this.enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }

    /** 未配置材质时按部位取默认皮革材质。 */
    public static Material defaultMaterial(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> Material.LEATHER_HELMET;
            case CHESTPLATE -> Material.LEATHER_CHESTPLATE;
            case LEGGINGS -> Material.LEATHER_LEGGINGS;
            case BOOTS -> Material.LEATHER_BOOTS;
        };
    }

    public ArmorSlot slot() {
        return slot;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public Material material() {
        return material;
    }

    public int customModelData() {
        return customModelData;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    /** CraftEngine 模型 ID（空表示使用原版材质 + CustomModelData）。 */
    public String craftEngineModel() {
        return craftEngineModel;
    }

    public boolean hasCraftEngineModel() {
        return craftEngineModel != null && !craftEngineModel.isEmpty();
    }

    /** 附魔：附魔 key（小写，如 protection）→ 等级。 */
    public Map<String, Integer> enchantments() {
        return enchantments;
    }
}
