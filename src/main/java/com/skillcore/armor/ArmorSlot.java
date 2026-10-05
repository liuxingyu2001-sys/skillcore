package com.skillcore.armor;

/**
 * 盔甲部位 — 头盔 / 胸甲 / 护腿 / 靴子。
 * <p>
 * 用于 PDC 标记（{@code skillcore_armor_slot}）与
 * {@link org.bukkit.inventory.PlayerInventory#getArmorContents()} 数组索引的映射。
 */
public enum ArmorSlot {

    HELMET("helmet", 3),
    CHESTPLATE("chestplate", 2),
    LEGGINGS("leggings", 1),
    BOOTS("boots", 0);

    /** 配置 / PDC 中使用的部位 key（小写）。 */
    private final String key;
    /** getArmorContents() 数组索引（0=boots, 1=leggings, 2=chestplate, 3=helmet）。 */
    private final int inventoryIndex;

    ArmorSlot(String key, int inventoryIndex) {
        this.key = key;
        this.inventoryIndex = inventoryIndex;
    }

    public String key() {
        return key;
    }

    public int inventoryIndex() {
        return inventoryIndex;
    }

    /** 按配置 key 解析（helmet/chestplate/leggings/boots），未知返回 null。 */
    public static ArmorSlot fromKey(String raw) {
        if (raw == null) {
            return null;
        }
        for (ArmorSlot slot : values()) {
            if (slot.key.equalsIgnoreCase(raw)) {
                return slot;
            }
        }
        return null;
    }
}
