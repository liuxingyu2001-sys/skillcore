package com.skillcore.armor;

import java.util.Map;

/**
 * 一套技能盔甲：头盔 / 胸甲 / 护腿 / 靴子 + 集齐整套后的加成与（可选的）盔甲技能。
 */
public final class ArmorSet {

    private final String id;
    private final String displayName;
    private final String description;
    private final ArmorPiece helmet;
    private final ArmorPiece chestplate;
    private final ArmorPiece leggings;
    private final ArmorPiece boots;
    private final ArmorStats stats;
    private final String skillType;

    public ArmorSet(String id, String displayName, String description,
                    ArmorPiece helmet, ArmorPiece chestplate,
                    ArmorPiece leggings, ArmorPiece boots,
                    ArmorStats stats, String skillType) {
        this.id = id;
        this.displayName = displayName == null ? id : displayName;
        this.description = description == null ? "" : description;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.stats = stats == null ? new ArmorStats() : stats;
        this.skillType = skillType;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public ArmorPiece helmet() {
        return helmet;
    }

    public ArmorPiece chestplate() {
        return chestplate;
    }

    public ArmorPiece leggings() {
        return leggings;
    }

    public ArmorPiece boots() {
        return boots;
    }

    public ArmorStats stats() {
        return stats;
    }

    /** 盔甲技能类型 key（可空；尚未实现盔甲技能前仅作占位）。 */
    public String skillType() {
        return skillType;
    }

    /** 按部位取部件。 */
    public ArmorPiece piece(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> helmet;
            case CHESTPLATE -> chestplate;
            case LEGGINGS -> leggings;
            case BOOTS -> boots;
        };
    }

    /** 四件套（供命令发放 / 遍历）。 */
    public Map<ArmorSlot, ArmorPiece> pieces() {
        return Map.of(
                ArmorSlot.HELMET, helmet,
                ArmorSlot.CHESTPLATE, chestplate,
                ArmorSlot.LEGGINGS, leggings,
                ArmorSlot.BOOTS, boots
        );
    }
}
