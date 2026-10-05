package com.skillcore.hook;

import com.skillcore.SkillCorePlugin;
import com.skillcore.weapon.SkillTrigger;
import com.skillcore.weapon.SkillWeapon;
import com.skillcore.weapon.WeaponItems;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 占位符：
 * <ul>
 *   <li>{@code %skillcore_weapon_id%}   手持技能武器 ID</li>
 *   <li>{@code %skillcore_weapon_name%} 手持技能武器显示名</li>
 *   <li>{@code %skillcore_has_weapon%}  是否手持技能武器</li>
 *   <li>{@code %skillcore_weapon_cd%}   右键技能冷却状态（就绪 / 3.2s）</li>
 *   <li>{@code %skillcore_weapon_cd_left%} / {@code _right%}</li>
 *   <li>{@code %skillcore_armor_set%}   当前穿戴整套的盔甲套装 ID</li>
 *   <li>{@code %skillcore_armor_set_name%} 套装显示名</li>
 *   <li>{@code %skillcore_has_armor_set%} 是否穿戴整套</li>
 * </ul>
 */
public final class SkillCorePlaceholders extends PlaceholderExpansion {

    private final SkillCorePlugin plugin;

    public SkillCorePlaceholders(SkillCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "skillcore";
    }

    @Override
    public String getAuthor() {
        return "SkillCore";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (!(offlinePlayer instanceof Player player)) {
            return "";
        }
        String weaponId = WeaponItems.getHeldWeaponId(plugin, player);
        String key = params.toLowerCase();
        if (key.equals("has_weapon")) {
            return String.valueOf(weaponId != null);
        }
        if (key.equals("weapon_id")) {
            return weaponId == null ? "" : weaponId;
        }
        if (key.equals("weapon_name")) {
            if (weaponId == null) return "";
            SkillWeapon weapon = plugin.getWeaponRegistry().get(weaponId);
            return weapon == null ? "" : weapon.displayName();
        }
        if (key.startsWith("weapon_cd")) {
            if (weaponId == null) {
                return plugin.getConfigManager().getConfig()
                        .getString("placeholders.no-weapon", "");
            }
            boolean left = key.endsWith("_left");
            SkillTrigger trigger = left ? SkillTrigger.LEFT_CLICK : SkillTrigger.RIGHT_CLICK;
            double remaining = plugin.getWeaponManager().getCooldownRemaining(player, weaponId, trigger);
            if (remaining <= 0) {
                return plugin.getConfigManager().getConfig()
                        .getString("placeholders.ready", "&a就绪");
            }
            return "&e" + String.format("%.1f", remaining) + "s";
        }
        if (key.equals("armor_set") || key.equals("armor_set_id")) {
            var set = plugin.getArmorManager() != null ? plugin.getArmorManager().getActiveSet(player) : null;
            return set == null ? "" : set.id();
        }
        if (key.equals("armor_set_name")) {
            var set = plugin.getArmorManager() != null ? plugin.getArmorManager().getActiveSet(player) : null;
            return set == null ? "" : set.displayName();
        }
        if (key.equals("has_armor_set")) {
            var set = plugin.getArmorManager() != null ? plugin.getArmorManager().getActiveSet(player) : null;
            return String.valueOf(set != null);
        }
        return null;
    }
}
