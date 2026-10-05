package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Area of effect (AOE) selection toolkit.
 */
public final class AreaUtils {

    private AreaUtils() {
    }

    /**
     * All living entities in a sphere around a location.
     */
    public static List<LivingEntity> getSphere(Location center, double radius) {
        return getSphere(center, radius, e -> true);
    }

    public static List<LivingEntity> getSphere(Location center, double radius, Predicate<LivingEntity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (center == null || center.getWorld() == null || radius <= 0) {
            return result;
        }
        double radiusSq = radius * radius;
        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (!DamageUtils.isAlive(entity)) {
                continue;
            }
            if (filter != null && !filter.test(entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Living entities in a cylinder (horizontal radius, vertical half-height).
     */
    public static List<LivingEntity> getCylinder(Location center, double radius, double halfHeight) {
        return getCylinder(center, radius, halfHeight, e -> true);
    }

    public static List<LivingEntity> getCylinder(Location center, double radius, double halfHeight, Predicate<LivingEntity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (center == null || center.getWorld() == null || radius <= 0) {
            return result;
        }
        double radiusSq = radius * radius;
        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (!DamageUtils.isAlive(entity)) {
                continue;
            }
            if (filter != null && !filter.test(entity)) {
                continue;
            }
            Location loc = entity.getLocation();
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            double dy = loc.getY() - center.getY();
            if (dx * dx + dz * dz <= radiusSq && Math.abs(dy) <= halfHeight) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Entities in a box (AABB) centered at location.
     */
    public static List<LivingEntity> getBox(Location center, double xRadius, double yRadius, double zRadius) {
        List<LivingEntity> result = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return result;
        }
        BoundingBox box = BoundingBox.of(center, xRadius, yRadius, zRadius);
        for (LivingEntity entity : center.getWorld().getLivingEntities()) {
            if (DamageUtils.isAlive(entity) && entity.getBoundingBox().overlaps(box)) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Line / column area between two points with a width radius.
     */
    public static List<LivingEntity> getLine(Location from, Location to, double width) {
        List<LivingEntity> result = new ArrayList<>();
        if (from == null || to == null || from.getWorld() == null) {
            return result;
        }
        World world = from.getWorld();
        double widthSq = width * width;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!DamageUtils.isAlive(entity)) {
                continue;
            }
            Location loc = entity.getLocation();
            if (distanceToSegment(loc, from, to) <= width) {
                result.add(entity);
            }
        }
        return result;
    }

    /**
     * Distance from point to line segment.
     */
    public static double distanceToSegment(Location point, Location a, Location b) {
        double ax = a.getX(), ay = a.getY(), az = a.getZ();
        double bx = b.getX(), by = b.getY(), bz = b.getZ();
        double px = point.getX(), py = point.getY(), pz = point.getZ();

        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double apx = px - ax, apy = py - ay, apz = pz - az;
        double abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 1.0e-9) {
            return Math.sqrt(apx * apx + apy * apy + apz * apz);
        }
        double t = (apx * abx + apy * aby + apz * abz) / abLenSq;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + abx * t - px;
        double cy = ay + aby * t - py;
        double cz = az + abz * t - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    /**
     * Filter out a set of excluded entities (e.g. caster + teammates).
     */
    public static List<LivingEntity> exclude(List<LivingEntity> input, LivingEntity... excluded) {
        List<LivingEntity> result = new ArrayList<>(input);
        for (LivingEntity e : excluded) {
            if (e != null) {
                result.remove(e);
            }
        }
        return result;
    }

    /**
     * Only players from a list.
     */
    public static List<Player> playersOnly(List<LivingEntity> input) {
        List<Player> result = new ArrayList<>();
        for (LivingEntity e : input) {
            if (e instanceof Player p) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Only non-player entities (mobs).
     */
    public static List<LivingEntity> mobsOnly(List<LivingEntity> input) {
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity e : input) {
            if (e instanceof Player) {
                continue;
            }
            result.add(e);
        }
        return result;
    }

    /**
     * Get entities in a horizontal ring (donut shape).
     */
    public static List<LivingEntity> getRing(Location center, double innerRadius, double outerRadius, double halfHeight) {
        List<LivingEntity> all = getCylinder(center, outerRadius, halfHeight);
        List<LivingEntity> result = new ArrayList<>();
        double innerSq = innerRadius * innerRadius;
        for (LivingEntity e : all) {
            double dx = e.getLocation().getX() - center.getX();
            double dz = e.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz >= innerSq) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * Random point in a circle (for scatter spells).
     */
    public static Location randomInCircle(Location center, double radius) {
        double angle = Math.random() * Math.PI * 2;
        double r = Math.sqrt(Math.random()) * radius;
        return center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }
}
