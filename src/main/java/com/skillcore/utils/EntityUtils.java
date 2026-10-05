package com.skillcore.utils;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Entity helper toolkit.
 */
public final class EntityUtils {

    private EntityUtils() {
    }

    public static boolean isValid(LivingEntity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public static boolean isPlayer(LivingEntity entity) {
        return entity instanceof Player;
    }

    public static boolean isNotPlayer(LivingEntity entity) {
        return entity != null && !(entity instanceof Player);
    }

    public static Player asPlayer(LivingEntity entity) {
        return entity instanceof Player player ? player : null;
    }

    /**
     * Whether player is in a playable survival-ish state.
     */
    public static boolean isPlayable(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        GameMode mode = player.getGameMode();
        return mode != GameMode.SPECTATOR;
    }

    /**
     * Horizontal distance between two entities.
     */
    public static double horizontalDistance(Entity a, Entity b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        Location la = a.getLocation();
        Location lb = b.getLocation();
        double dx = la.getX() - lb.getX();
        double dz = la.getZ() - lb.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Distance between two entities (3D).
     */
    public static double distance(Entity a, Entity b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        return a.getLocation().distance(b.getLocation());
    }

    /**
     * Whether entity is within range of a location.
     */
    public static boolean isInRange(Entity entity, Location center, double range) {
        if (entity == null || center == null) {
            return false;
        }
        return entity.getLocation().distanceSquared(center) <= range * range;
    }

    /**
     * Get entities in world matching a filter (living only).
     */
    public static List<LivingEntity> filter(World world, Predicate<LivingEntity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (world == null) {
            return result;
        }
        for (LivingEntity entity : world.getLivingEntities()) {
            if (isValid(entity) && (filter == null || filter.test(entity))) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * All players in a world.
     */
    public static List<Player> playersIn(World world) {
        return world == null ? List.of() : new ArrayList<>(world.getPlayers());
    }

    /**
     * All online players.
     */
    public static List<Player> allPlayers() {
        return new ArrayList<>(org.bukkit.Bukkit.getOnlinePlayers());
    }

    /**
     * Direction vector from a to b.
     */
    public static Vector direction(Entity from, Entity to) {
        if (from == null || to == null) {
            return new Vector();
        }
        return to.getLocation().toVector().subtract(from.getLocation().toVector());
    }

    /**
     * Direction from location to entity.
     */
    public static Vector direction(Location from, Entity to) {
        if (from == null || to == null) {
            return new Vector();
        }
        return to.getLocation().toVector().subtract(from.toVector());
    }

    /**
     * Look direction of entity as a unit vector (horizontal only).
     */
    public static Vector flatLook(Entity entity) {
        if (entity == null) {
            return new Vector();
        }
        Vector dir = entity.getLocation().getDirection().clone();
        dir.setY(0);
        return dir.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : dir.normalize();
    }

    /**
     * Heal to full.
     */
    public static void fullHeal(LivingEntity entity) {
        if (isValid(entity)) {
            entity.setHealth(DamageUtils.getMaxHealth(entity));
        }
    }

    /**
     * Kill safely.
     */
    public static void kill(LivingEntity entity) {
        if (isValid(entity)) {
            entity.setHealth(0);
        }
    }

    /**
     * Whether target is a valid enemy of caster (simple: not same player, not tamed if owner).
     */
    public static boolean isEnemy(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || caster.equals(target)) {
            return false;
        }
        if (!isValid(target)) {
            return false;
        }
        if (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return true;
    }

    /**
     * Freeze entity velocity (stop movement).
     */
    public static void freeze(Entity entity) {
        if (entity != null) {
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    /**
     * Whether entity is on ground (approx via velocity + location).
     */
    public static boolean isOnGround(LivingEntity entity) {
        return entity != null && entity.isOnGround();
    }

    /**
     * Get body center location of entity.
     */
    public static Location bodyCenter(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
    }

    /**
     * Safe scoreboard-friendly display name string.
     */
    public static String displayName(LivingEntity entity) {
        if (entity == null) {
            return "Unknown";
        }
        return entity instanceof Player player ? player.getName() : entity.getType().name();
    }
}
