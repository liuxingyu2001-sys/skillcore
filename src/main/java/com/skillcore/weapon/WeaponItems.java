package com.skillcore.weapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能武器物品构建 / 识别 / lore 更新。
 */
public final class WeaponItems {

    public static final String KEY_WEAPON_ID = "skillcore_weapon_id";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private WeaponItems() {
    }

    /**
     * 根据定义生成物品（lore 自动填入数值变量）。
     */
    public static ItemStack create(SkillWeapon weapon) {
        if (weapon == null) return new ItemStack(Material.AIR);
        ItemStack item = new ItemStack(weapon.material(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(LEGACY.deserialize(
                weapon.displayName() == null ? weapon.id() : weapon.displayName()));

        List<String> rawLore = weapon.lore();
        List<String> loreLines = (rawLore == null || rawLore.isEmpty())
                ? WeaponLore.defaultLore(weapon)
                : WeaponLore.applyAll(rawLore, weapon);

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(LEGACY.deserialize(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (weapon.customModelData() > 0) {
            meta.setCustomModelData(weapon.customModelData());
        }
        if (weapon.glow()) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);

        Plugin plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin != null) {
            mark(plugin, item, weapon.id());
        }
        return item;
    }

    /**
     * 手动更新物品 lore（管理员命令用）— 按当前 weapons.yml 数值刷新占位符。
     *
     * @return true 表示物品是技能武器且已更新
     */
    public static boolean updateLore(ItemStack item, SkillWeapon weapon) {
        if (item == null || item.getType() == Material.AIR || weapon == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 显示名也可含变量
        meta.displayName(LEGACY.deserialize(
                WeaponLore.apply(weapon.displayName() == null ? weapon.id() : weapon.displayName(), weapon)));

        List<String> rawLore = weapon.lore();
        List<String> loreLines = (rawLore == null || rawLore.isEmpty())
                ? WeaponLore.defaultLore(weapon)
                : WeaponLore.applyAll(rawLore, weapon);

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(LEGACY.deserialize(line));
        }
        meta.lore(lore);

        if (weapon.customModelData() > 0) {
            meta.setCustomModelData(weapon.customModelData());
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 给任意物品打上 weapon_id。
     */
    public static void mark(Plugin plugin, ItemStack item, String weaponId) {
        if (plugin == null || item == null || weaponId == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, KEY_WEAPON_ID),
                PersistentDataType.STRING,
                weaponId
        );
        item.setItemMeta(meta);
    }

    /**
     * 读取 weapon_id。
     */
    public static String getWeaponId(Plugin plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_WEAPON_ID),
                PersistentDataType.STRING
        );
    }

    public static boolean isSkillWeapon(Plugin plugin, ItemStack item) {
        return getWeaponId(plugin, item) != null;
    }

    public static String getHeldWeaponId(Plugin plugin, org.bukkit.entity.Player player) {
        return getWeaponId(plugin, player.getInventory().getItemInMainHand());
    }
}
