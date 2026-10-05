package com.skillcore.armor;

import com.skillcore.SkillCorePlugin;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 盔甲套装管理 — 检测玩家是否穿戴整套，维护每个玩家当前生效的加成。
 * <p>
 * 集齐整套后：{@link #getActive(Player)} 返回该套装的 {@link ArmorStats}，
 * 战斗事件据此叠加吸血 / 反伤 / 增伤 / 减伤；后续盔甲技能在此接入生命周期
 * （{@link ArmorSkill#onEquip} / {@link ArmorSkill#onUnequip} / {@link ArmorSkill#onTick}）。
 */
public final class ArmorManager {

    private final SkillCorePlugin plugin;
    private final ArmorRegistry registry;
    private final ArmorFactory factory;

    /** player -> 当前生效的整套套装 id。 */
    private final Map<UUID, String> activeSets = new ConcurrentHashMap<>();
    /** player -> 当前生效的盔甲技能实例。 */
    private final Map<UUID, ArmorSkill> activeSkills = new ConcurrentHashMap<>();

    /** 被动属性修饰符固定 UUID（同一玩家同时只会穿戴一整套，固定即可避免叠加）。 */
    private static final UUID MAX_HEALTH_UUID = UUID.fromString("8f4d6b7e-0001-0000-0000-000000000001");
    private static final UUID MOVE_SPEED_UUID = UUID.fromString("8f4d6b7e-0001-0000-0000-000000000002");

    public ArmorManager(SkillCorePlugin plugin, ArmorRegistry registry, ArmorFactory factory) {
        this.plugin = plugin;
        this.registry = registry;
        this.factory = factory;
    }

    public ArmorRegistry registry() {
        return registry;
    }

    /**
     * 重新检测某玩家是否穿戴整套技能盔甲并更新生效状态。
     * <p>
     * 套装变更时同步切换盔甲技能：先脱旧技能（onUnequip + cleanup），再穿新技能（onEquip）。
     */
    public void recompute(Player player) {
        if (player == null || !player.isOnline()) {
            if (player != null) {
                activeSets.remove(player.getUniqueId());
                unequipSkill(player, null);
            }
            return;
        }
        String setId = detectFullSet(player);
        UUID uuid = player.getUniqueId();
        String prev = activeSets.get(uuid);
        if (setId == null) {
            if (prev != null) {
                activeSets.remove(uuid);
                removeAllPassiveAttributes(player);
                unequipSkill(player, registry.get(prev));
            }
            return;
        }
        if (setId.equals(prev)) {
            return;
        }
        activeSets.put(uuid, setId);
        ArmorSet prevSet = prev == null ? null : registry.get(prev);
        ArmorSet newSet = registry.get(setId);
        if (prevSet != null) {
            removeAllPassiveAttributes(player);
            unequipSkill(player, prevSet);
        }
        applyPassiveAttributes(player, newSet);
        equipSkill(player, newSet);
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info(player.getName() + " 穿戴整套盔甲: " + setId);
        }
    }

    /** 应用套装的属性加成（最大生命 / 移动速度）。 */
    private void applyPassiveAttributes(Player player, ArmorSet set) {
        if (set == null) {
            return;
        }
        ArmorStats s = set.stats();
        if (s.maxHealthBonus() > 0) {
            applyAttribute(player, Attribute.MAX_HEALTH, MAX_HEALTH_UUID,
                    "skillcore_armor_max_health", s.maxHealthBonus(),
                    AttributeModifier.Operation.ADD_NUMBER);
        }
        if (Math.abs(s.movementSpeedPercent()) > 1.0e-9) {
            applyAttribute(player, Attribute.MOVEMENT_SPEED, MOVE_SPEED_UUID,
                    "skillcore_armor_move_speed", s.movementSpeedPercent(),
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }
    }

    private void applyAttribute(Player player, Attribute attribute, UUID uuid,
                                String name, double amount, AttributeModifier.Operation op) {
        var attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        // 先移除同 UUID 旧修饰符，避免叠加
        for (AttributeModifier m : new java.util.ArrayList<>(attr.getModifiers())) {
            if (uuid.equals(m.getUniqueId())) {
                attr.removeModifier(m);
            }
        }
        attr.addModifier(new AttributeModifier(uuid, name, amount, op));
    }

    /** 移除该玩家的全部盔甲属性加成。 */
    private void removeAllPassiveAttributes(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        removeAttribute(player, Attribute.MAX_HEALTH, MAX_HEALTH_UUID);
        removeAttribute(player, Attribute.MOVEMENT_SPEED, MOVE_SPEED_UUID);
    }

    private void removeAttribute(Player player, Attribute attribute, UUID uuid) {
        var attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        for (AttributeModifier m : new java.util.ArrayList<>(attr.getModifiers())) {
            if (uuid.equals(m.getUniqueId())) {
                attr.removeModifier(m);
            }
        }
    }

    /** 实例化并触发套装的盔甲技能 onEquip。 */
    private void equipSkill(Player player, ArmorSet set) {
        if (set == null || set.skillType() == null || set.skillType().isEmpty()) {
            return;
        }
        ArmorSkill skill = factory.createSkill(set.skillType(), set);
        if (skill == null) {
            return;
        }
        activeSkills.put(player.getUniqueId(), skill);
        try {
            skill.onEquip(new ArmorContext(player, set));
        } catch (Exception ex) {
            plugin.getLogger().severe("Armor skill equip error [" + set.id() + "/" + skill.getType() + "]: " + ex);
            ex.printStackTrace();
        }
    }

    /** 触发旧盔甲技能的 onUnequip 并清理。 */
    private void unequipSkill(Player player, ArmorSet previousSet) {
        ArmorSkill skill = activeSkills.remove(player.getUniqueId());
        if (skill == null) {
            return;
        }
        try {
            skill.onUnequip(new ArmorContext(player, previousSet));
        } catch (Exception ex) {
            plugin.getLogger().severe("Armor skill unequip error: " + ex);
        }
        try {
            skill.cleanup();
        } catch (Exception ex) {
            plugin.getLogger().severe("Armor skill cleanup error: " + ex);
        }
    }

    /**
     * 检测玩家四件盔甲是否属于同一套装且部位正确。
     *
     * @return 套装 id（未集齐返回 null）
     */
    public String detectFullSet(Player player) {
        ItemStack[] contents = player.getInventory().getArmorContents();
        if (contents == null || contents.length < ArmorSlot.values().length) {
            return null;
        }
        String setId = null;
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack item = contents[slot.inventoryIndex()];
            if (item == null || item.getType() == Material.AIR) {
                return null;
            }
            String pieceSet = ArmorItems.getSetId(plugin, item);
            String pieceSlot = ArmorItems.getSlot(plugin, item);
            if (pieceSet == null || !slot.key().equalsIgnoreCase(pieceSlot)) {
                return null;
            }
            if (setId == null) {
                setId = pieceSet;
            } else if (!setId.equalsIgnoreCase(pieceSet)) {
                return null;
            }
        }
        return setId;
    }

    /** 玩家当前生效的套装加成（未穿戴整套返回 {@link ArmorStats#EMPTY}）。 */
    public ArmorStats getActive(Player player) {
        if (player == null) {
            return ArmorStats.EMPTY;
        }
        String setId = activeSets.get(player.getUniqueId());
        if (setId == null) {
            return ArmorStats.EMPTY;
        }
        ArmorSet set = registry.get(setId);
        return set == null ? ArmorStats.EMPTY : set.stats();
    }

    /** 玩家当前生效的套装（未穿戴整套返回 null）。 */
    public ArmorSet getActiveSet(Player player) {
        if (player == null) {
            return null;
        }
        String setId = activeSets.get(player.getUniqueId());
        return setId == null ? null : registry.get(setId);
    }

    /** 重算全部在线玩家（周期兜底检测）。 */
    public void recomputeAllOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            recompute(player);
        }
    }

    /**
     * 对当前生效的盔甲技能派发 onTick（每 tick 调用）。
     * <p>
     * 只遍历穿戴整套的玩家（activeSkills），未覆写 onTick 的技能为空操作，开销极小。
     */
    public void tickAllOnline() {
        if (activeSkills.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, ArmorSkill> entry : activeSkills.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            String setId = activeSets.get(entry.getKey());
            ArmorSet set = setId == null ? null : registry.get(setId);
            if (set == null) {
                continue;
            }
            try {
                entry.getValue().onTick(new ArmorContext(player, set));
            } catch (Exception ex) {
                plugin.getLogger().severe("Armor skill tick error: " + ex);
            }
        }
    }

    /** 玩家退出时清理。 */
    public void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        activeSets.remove(playerId);
        ArmorSkill skill = activeSkills.remove(playerId);
        if (skill != null) {
            try {
                skill.cleanup();
            } catch (Exception ex) {
                plugin.getLogger().severe("Armor skill cleanup error: " + ex);
            }
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            removeAllPassiveAttributes(player);
        }
    }

    /** 重载 / 禁用时清理全部状态。 */
    public void clearAll() {
        for (ArmorSkill skill : activeSkills.values()) {
            try {
                skill.cleanup();
            } catch (Exception ex) {
                plugin.getLogger().severe("Armor skill cleanup error: " + ex);
            }
        }
        activeSkills.clear();
        activeSets.clear();
        // 移除所有在线玩家的盔甲属性加成
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeAllPassiveAttributes(player);
        }
    }

    /**
     * 发放整套盔甲给玩家（四件）。
     *
     * @return 是否发放成功
     */
    public boolean giveSet(Player player, String setId) {
        ArmorSet set = registry.get(setId);
        if (set == null || player == null) {
            return false;
        }
        boolean any = false;
        for (ArmorPiece piece : set.pieces().values()) {
            if (piece == null) {
                continue;
            }
            ItemStack item = ArmorItems.create(set, piece);
            if (item.getType() == Material.AIR) {
                continue;
            }
            player.getInventory().addItem(item);
            any = true;
        }
        return any;
    }
}
