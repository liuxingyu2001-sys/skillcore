package com.skillcore.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 统一目标过滤 — 所有 AOE / 射线 / 吸附 / 路径伤害都应经过这里，
 * 避免打到友军、宠物、NPC、盔甲架或 PvP 禁用世界的玩家。
 * <p>
 * 由插件启动 / 重载时调用 {@link #load(FileConfiguration)} 注入配置。
 */
public final class TargetFilter {

    private static volatile boolean ignoreArmorStands = true;
    private static volatile boolean ignoreNpcs = true;
    private static volatile boolean ignoreOwnPets = true;
    private static volatile boolean ignoreSameTeam = true;
    private static volatile Set<String> pvpBlockedWorlds = Set.of();

    private TargetFilter() {
    }

    public static void load(FileConfiguration config) {
        if (config == null) return;
        ignoreArmorStands = config.getBoolean("target-filter.ignore-armor-stands", true);
        ignoreNpcs = config.getBoolean("target-filter.ignore-npcs", true);
        ignoreOwnPets = config.getBoolean("target-filter.ignore-own-pets", true);
        ignoreSameTeam = config.getBoolean("target-filter.ignore-same-team", true);

        Set<String> blocked = new HashSet<>();
        for (String world : config.getStringList("combat.pvp-blocked-worlds")) {
            blocked.add(world.toLowerCase(Locale.ROOT));
        }
        for (String world : config.getStringList("target-filter.pvp-blocked-worlds")) {
            blocked.add(world.toLowerCase(Locale.ROOT));
        }
        pvpBlockedWorlds = Set.copyOf(blocked);
    }

    /**
     * 是否应忽略该目标（true = 不可被技能选中）。
     */
    public static boolean shouldIgnoreTarget(LivingEntity attacker, LivingEntity target) {
        if (target == null || attacker == null) return true;
        if (target.equals(attacker)) return true;
        if (!DamageUtils.isAlive(target)) return true;
        if (target instanceof Player player && DamageUtils.isInvulnerable(player)) return true;

        if (ignoreArmorStands && target instanceof ArmorStand) return true;

        Entity current = target;
        if (current.getVehicle() != null && current.getVehicle().equals(attacker)) return true;

        if (ignoreNpcs && target.hasMetadata("NPC")) return true;

        if (ignoreOwnPets && target instanceof Tameable tameable && tameable.isTamed()
                && tameable.getOwner() != null
                && tameable.getOwner().getUniqueId().equals(attacker.getUniqueId())) {
            return true;
        }

        // PvP 禁用世界：玩家之间互不伤害
        if (attacker instanceof Player && target instanceof Player) {
            if (pvpBlockedWorlds.contains(attacker.getWorld().getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (ignoreSameTeam && sameTeam((Player) attacker, (Player) target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameTeam(Player a, Player b) {
        var scoreboard = a.getScoreboard();
        var team = scoreboard.getEntryTeam(a.getName());
        return team != null && team.hasEntry(b.getName());
    }

    /**
     * 过滤掉应忽略的目标。
     */
    public static List<LivingEntity> filter(LivingEntity attacker, List<LivingEntity> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<LivingEntity> out = new ArrayList<>(input.size());
        for (LivingEntity e : input) {
            if (!shouldIgnoreTarget(attacker, e)) {
                out.add(e);
            }
        }
        return out;
    }

    /** 供 Predicate 使用。 */
    public static boolean isAttackable(LivingEntity attacker, LivingEntity target) {
        return !shouldIgnoreTarget(attacker, target);
    }
}
