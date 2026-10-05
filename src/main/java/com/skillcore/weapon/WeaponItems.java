package com.skillcore.weapon;

import net.kyori.adventure.text.Component;
import com.skillcore.utils.TextUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
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

    private WeaponItems() {
    }

    /**
     * 根据定义生成物品（lore 自动填入数值变量）。
     * <p>
     * 若配置了 {@code craftengine_model} 且已安装 CraftEngine，则用该模型作为底物，
     * 否则降级原版材质 + CustomModelData。
     */
    public static ItemStack create(SkillWeapon weapon) {
        if (weapon == null) return new ItemStack(Material.AIR);
        ItemStack item = null;
        boolean ceApplied = false;
        if (weapon.hasCraftEngineModel()) {
            item = com.skillcore.hook.CraftEngineHook.buildItem(weapon.craftEngineModel());
            ceApplied = item != null;
        }
        if (item == null) {
            item = new ItemStack(weapon.material(), 1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        applyMeta(meta, weapon, ceApplied);
        item.setItemMeta(meta);

        Plugin plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin != null) {
            mark(plugin, item, weapon.id());
        }
        return item;
    }

    /**
     * 手动更新物品 lore（管理员命令用）— 按当前 skills/ 目录数值刷新占位符。
     *
     * @return true 表示物品是技能武器且已更新
     */
    public static boolean updateLore(ItemStack item, SkillWeapon weapon) {
        if (item == null || item.getType() == Material.AIR || weapon == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        applyMeta(meta, weapon, com.skillcore.hook.CraftEngineHook.isCustomItem(item));
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 统一写入显示名（MiniMessage / & 兼容）、lore、附魔、不可破坏、模型。
     */
    private static void applyMeta(ItemMeta meta, SkillWeapon weapon, boolean ceApplied) {
        String name = weapon.displayName() == null ? weapon.id() : weapon.displayName();
        meta.displayName(TextUtils.parse(WeaponLore.apply(name, weapon)));

        List<String> rawLore = weapon.lore();
        List<String> loreLines = (rawLore == null || rawLore.isEmpty())
                ? WeaponLore.defaultLore(weapon)
                : WeaponLore.applyAll(rawLore, weapon);
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(TextUtils.parse(line));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        // CE 模型用 item_model 组件，不再叠加 CustomModelData
        if (!ceApplied && weapon.customModelData() > 0) {
            meta.setCustomModelData(weapon.customModelData());
        }
        if (weapon.unbreakable()) {
            meta.setUnbreakable(true);
        }

        boolean hasEnchants = applyEnchantments(meta, weapon);
        if (weapon.glow() && !hasEnchants) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    /**
     * 按配置写入附魔（允许超过原版上限，如 wind_burst: 5）。
     *
     * @return 是否写入了至少一个附魔
     */
    private static boolean applyEnchantments(ItemMeta meta, SkillWeapon weapon) {
        if (weapon.enchantments().isEmpty()) return false;
        boolean any = false;
        for (var entry : weapon.enchantments().entrySet()) {
            Enchantment enchantment = resolveEnchantment(entry.getKey());
            if (enchantment != null && entry.getValue() > 0) {
                meta.addEnchant(enchantment, entry.getValue(), true);
                any = true;
            }
        }
        return any;
    }

    /**
     * 解析附魔 key（如 wind_burst / minecraft:sharpness）。
     */
    public static Enchantment resolveEnchantment(String key) {
        if (key == null || key.isEmpty()) return null;
        NamespacedKey namespacedKey = key.contains(":")
                ? NamespacedKey.fromString(key)
                : NamespacedKey.minecraft(key.toLowerCase());
        if (namespacedKey == null) return null;
        try {
            return org.bukkit.Registry.ENCHANTMENT.get(namespacedKey);
        } catch (Exception ex) {
            return null;
        }
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
