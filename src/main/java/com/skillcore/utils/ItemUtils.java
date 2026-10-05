package com.skillcore.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemStack / NBT (PDC) toolkit for skill items and bindings.
 */
public final class ItemUtils {

    private ItemUtils() {
    }

    /**
     * Create a simple item.
     */
    public static ItemStack create(Material material, int amount) {
        return new ItemStack(material == null ? Material.AIR : material, Math.max(1, amount));
    }

    /**
     * Create an item with display name and lore.
     */
    public static ItemStack create(Material material, String displayName, List<String> lore) {
        ItemStack item = create(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (displayName != null) {
            meta.displayName(MessageUtils.component(displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            List<Component> components = new ArrayList<>();
            for (String line : lore) {
                components.add(MessageUtils.component(line));
            }
            meta.lore(components);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Whether item is null or air.
     */
    public static boolean isNullOrEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /**
     * Set persistent data string.
     */
    public static void setString(Plugin plugin, ItemStack item, String key, String value) {
        if (isNullOrEmpty(item) || plugin == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    /**
     * Get persistent data string.
     */
    public static String getString(Plugin plugin, ItemStack item, String key) {
        if (isNullOrEmpty(item) || plugin == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
    }

    /**
     * Set persistent data double.
     */
    public static void setDouble(Plugin plugin, ItemStack item, String key, double value) {
        if (isNullOrEmpty(item) || plugin == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, key), PersistentDataType.DOUBLE, value);
        item.setItemMeta(meta);
    }

    public static Double getDouble(Plugin plugin, ItemStack item, String key) {
        if (isNullOrEmpty(item) || plugin == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(new NamespacedKey(plugin, key), PersistentDataType.DOUBLE);
    }

    /**
     * Whether item has a specific PDC key.
     */
    public static boolean hasKey(Plugin plugin, ItemStack item, String key) {
        if (isNullOrEmpty(item) || plugin == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(new NamespacedKey(plugin, key));
    }

    /**
     * Mark an item as a skill item with skill id.
     */
    public static void markSkillItem(Plugin plugin, ItemStack item, String skillId) {
        setString(plugin, item, "skill_id", skillId);
    }

    /**
     * Read skill id from a skill item.
     */
    public static String getSkillId(Plugin plugin, ItemStack item) {
        return getString(plugin, item, "skill_id");
    }

    /**
     * Compare two items for material + amount (ignore meta).
     */
    public static boolean isSameType(ItemStack a, ItemStack b) {
        if (isNullOrEmpty(a) || isNullOrEmpty(b)) {
            return false;
        }
        return a.getType() == b.getType();
    }

    /**
     * Clone safely.
     */
    public static ItemStack cloneSafe(ItemStack item) {
        return isNullOrEmpty(item) ? null : item.clone();
    }

    /**
     * Consume one item from hand.
     */
    public static void consumeOne(ItemStack item) {
        if (isNullOrEmpty(item)) {
            return;
        }
        int amount = item.getAmount() - 1;
        if (amount <= 0) {
            item.setAmount(0);
            item.setType(Material.AIR);
        } else {
            item.setAmount(amount);
        }
    }

    /**
     * Lore helpers.
     */
    public static void addLoreLine(ItemStack item, String line) {
        if (isNullOrEmpty(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = meta.lore();
        List<Component> newLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        newLore.add(MessageUtils.component(line));
        meta.lore(newLore);
        item.setItemMeta(meta);
    }

    public static void setDisplayName(ItemStack item, String name) {
        if (isNullOrEmpty(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.displayName(MessageUtils.component(name));
        item.setItemMeta(meta);
    }
}
