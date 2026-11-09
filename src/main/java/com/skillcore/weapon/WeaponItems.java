package com.skillcore.weapon;

import net.kyori.adventure.text.Component;
import com.skillcore.utils.TextUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
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

    /** 普攻攻击力属性修饰符的 key（重建物品前先移除同名项，避免反复 applyMeta 叠加）。 */
    private static final NamespacedKey ATTACK_DAMAGE_KEY =
            new NamespacedKey("skillcore", "weapon_attack_damage");

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

        applyMeta(meta, weapon, ceApplied, item.getType());
        // PDC 与外观一次写入，避免 create 里再克隆一次 ItemMeta
        Plugin plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, KEY_WEAPON_ID),
                    PersistentDataType.STRING,
                    weapon.id());
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 手动更新物品 lore（管理员命令用）— 按当前 skills/ 目录数值刷新占位符。
     *
     * @return true 表示物品是技能武器且已更新
     */
    public static boolean updateLore(ItemStack item, SkillWeapon weapon) {
        if (item == null || item.getType() == Material.AIR || weapon == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        applyMeta(meta, weapon, com.skillcore.hook.CraftEngineHook.isCustomItem(item), item.getType());
        item.setItemMeta(meta);
        return true;
    }

    /**
     * 统一写入显示名（MiniMessage / & 兼容）、lore、附魔、不可破坏、模型。
     */
    private static void applyMeta(ItemMeta meta, SkillWeapon weapon, boolean ceApplied, Material material) {
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

        applyAttackDamage(meta, weapon);

        boolean hasEnchants = applyEnchantments(meta, weapon);
        // 旧版 glow = 「隐藏附魔 海之眷顾 I + HIDE_ENCHANTS」：副作用是把玩家后来附上的附魔
        // 一起藏掉（表现为「武器不显示任何附魔」），重建物品时顺手清掉这层遗留。
        clearLegacyGlow(meta, material);
        if (weapon.glow() && !hasEnchants) {
            // 1.20.5+ 的 enchantment_glint_override 组件：只发光，不产生附魔、不隐藏附魔行
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
    }

    /**
     * 写入 {@code attack-damage} 属性（0 = 不改原版普攻）。
     * <p>
     * 锄头 / 法杖这类底物在原版只有 1 点普攻伤害，靠这个修饰符才能当正常武器用。
     * 每次重建物品前先移除本插件写入的同名修饰符，否则 {@code /sc updatelore}
     * 会把修饰符叠成多份、普攻伤害翻倍。
     */
    private static void applyAttackDamage(ItemMeta meta, SkillWeapon weapon) {
        var existing = meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (existing != null && !existing.isEmpty()) {
            for (AttributeModifier modifier : new ArrayList<>(existing)) {
                if (ATTACK_DAMAGE_KEY.equals(modifier.getKey())) {
                    meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
                }
            }
        }
        double amount = weapon.stats() == null ? 0.0 : weapon.stats().attackDamage();
        if (!Double.isFinite(amount) || amount <= 0) return;
        // 上限与 WeaponStats.sanitize 保持一致，配置写错一个数量级不会变成一击必杀
        double safe = Math.min(amount, 10_000.0);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(ATTACK_DAMAGE_KEY, safe,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND));
    }

    /**
     * 清理旧版 glow 实现留下的痕迹（隐藏附魔 + 隐藏附魔行标记）。
     * <p>
     * 判定条件刻意收窄：只有「海之眷顾 I + HIDE_ENCHANTS」这个组合才可能是插件为发光塞进去的
     * ——正常武器不可能获得该附魔；钓鱼竿本来就是该附魔的合法载体，一律不动。
     */
    private static void clearLegacyGlow(ItemMeta meta, Material material) {
        if (material == null || material == Material.FISHING_ROD) return;
        if (!meta.hasEnchant(Enchantment.LUCK_OF_THE_SEA)) return;
        if (meta.getEnchantLevel(Enchantment.LUCK_OF_THE_SEA) != 1) return;
        if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) return;
        meta.removeEnchant(Enchantment.LUCK_OF_THE_SEA);
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
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
        if (key == null || key.isBlank()) return null;
        try {
            String trimmed = key.trim();
            NamespacedKey namespacedKey = trimmed.contains(":")
                    ? NamespacedKey.fromString(trimmed)
                    : NamespacedKey.minecraft(trimmed.toLowerCase(java.util.Locale.ROOT));
            if (namespacedKey == null) return null;
            return org.bukkit.Registry.ENCHANTMENT.get(namespacedKey);
        } catch (Exception ex) {
            // 配置里写错附魔 key 时不要让整个物品构建失败
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
        // 无 meta 的普通物品直接跳过，避免每次点击都克隆 ItemMeta（性能热点）
        if (!item.hasItemMeta()) return null;
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

    /**
     * 是否是旧版 glow 写法留下的物品（隐藏附魔 海之眷顾 I + HIDE_ENCHANTS）。
     * <p>
     * 这种组合会隐藏物品上所有附魔行，需要重建一次物品来修复。判定条件收窄，
     * 钓鱼竿本来就是该附魔的合法载体，一律不算。
     */
    public static boolean isLegacyGlowItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.FISHING_ROD) {
            return false;
        }
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.hasEnchant(Enchantment.LUCK_OF_THE_SEA)
                && meta.getEnchantLevel(Enchantment.LUCK_OF_THE_SEA) == 1
                && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS);
    }

    public static String getHeldWeaponId(Plugin plugin, org.bukkit.entity.Player player) {
        if (player == null) return null;
        return getWeaponId(plugin, player.getInventory().getItemInMainHand());
    }
}
