package com.skillcore.armor;

import com.skillcore.utils.TextUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能盔甲物品构建 / 识别 / PDC 标记。
 * <p>
 * 每件盔甲带两个 PDC 键：套装 id（{@code skillcore_armor_set}）
 * 与部位（{@code skillcore_armor_slot}）。
 */
public final class ArmorItems {

    public static final String KEY_ARMOR_SET = "skillcore_armor_set";
    public static final String KEY_ARMOR_SLOT = "skillcore_armor_slot";

    private ArmorItems() {
    }

    /** 按部件定义生成一件盔甲（带套装 id + 部位 PDC）。 */
    public static ItemStack create(ArmorSet set, ArmorPiece piece) {
        if (set == null || piece == null) {
            return new ItemStack(Material.AIR);
        }
        // 配置了 craftengine_model 时优先用 CraftEngine 模型作底物，失败降级原版材质
        ItemStack item = null;
        boolean ceApplied = false;
        if (piece.hasCraftEngineModel()) {
            item = com.skillcore.hook.CraftEngineHook.buildItem(piece.craftEngineModel());
            ceApplied = item != null;
        }
        if (item == null) {
            item = new ItemStack(piece.material(), 1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = piece.displayName() == null ? set.displayName() : piece.displayName();
        meta.displayName(TextUtils.parse(name));

        if (piece.lore() != null && !piece.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : piece.lore()) {
                lore.add(TextUtils.parse(line));
            }
            meta.lore(lore);
        }
        // CE 模型走 item_model 组件，不再叠加 CustomModelData
        if (!ceApplied && piece.customModelData() > 0) {
            meta.setCustomModelData(piece.customModelData());
        }
        if (piece.unbreakable()) {
            meta.setUnbreakable(true);
        }
        applyEnchantments(meta, piece);
        item.setItemMeta(meta);

        Plugin plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin != null) {
            mark(plugin, item, set.id(), piece.slot().key());
        }
        return item;
    }

    /** 写入附魔（复用武器系统的附魔 key 解析，允许超过原版上限）。 */
    private static void applyEnchantments(ItemMeta meta, ArmorPiece piece) {
        if (piece.enchantments().isEmpty()) {
            return;
        }
        for (var entry : piece.enchantments().entrySet()) {
            Enchantment enchantment = com.skillcore.weapon.WeaponItems.resolveEnchantment(entry.getKey());
            if (enchantment != null && entry.getValue() > 0) {
                meta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
    }

    /** 打上套装 id + 部位 PDC。 */
    public static void mark(Plugin plugin, ItemStack item, String setId, String slotKey) {
        if (plugin == null || item == null || setId == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, KEY_ARMOR_SET), PersistentDataType.STRING, setId);
        if (slotKey != null) {
            pdc.set(new NamespacedKey(plugin, KEY_ARMOR_SLOT), PersistentDataType.STRING, slotKey);
        }
        item.setItemMeta(meta);
    }

    /** 读取套装 id。 */
    public static String getSetId(Plugin plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() == Material.AIR) {
            return null;
        }
        // 无 meta 直接跳过，避免频繁克隆 ItemMeta
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_ARMOR_SET), PersistentDataType.STRING);
    }

    /** 读取部位 key（helmet/chestplate/leggings/boots）。 */
    public static String getSlot(Plugin plugin, ItemStack item) {
        if (plugin == null || item == null || item.getType() == Material.AIR) {
            return null;
        }
        if (!item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(plugin, KEY_ARMOR_SLOT), PersistentDataType.STRING);
    }

    /** 是否技能盔甲部件（带套装 id PDC）。 */
    public static boolean isArmorPiece(Plugin plugin, ItemStack item) {
        return getSetId(plugin, item) != null;
    }
}
