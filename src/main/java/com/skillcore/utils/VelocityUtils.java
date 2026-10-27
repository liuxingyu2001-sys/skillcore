package com.skillcore.utils;

import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Velocity / knockback helper toolkit.
 */
public final class VelocityUtils {

    private VelocityUtils() {
    }

    /**
     * Normalize and scale a direction vector.
     */
    public static Vector direction(Vector from, Vector to, double speed) {
        if (from == null || to == null || !Double.isFinite(speed)) {
            return new Vector();
        }
        Vector dir = to.clone().subtract(from);
        if (dir.lengthSquared() < 1.0e-9) {
            return new Vector();
        }
        return dir.normalize().multiply(speed);
    }

    /**
     * Add velocity with clamping.
     */
    public static void addVelocity(Entity entity, Vector velocity, double maxTotal) {
        if (entity == null || velocity == null || !finite(velocity)) {
            return;
        }
        Vector result = entity.getVelocity().add(velocity);
        if (maxTotal > 0 && result.length() > maxTotal) {
            result = result.normalize().multiply(maxTotal);
        }
        entity.setVelocity(result);
    }

    /**
     * Set horizontal velocity keeping vertical component.
     */
    public static void setHorizontalVelocity(Entity entity, double x, double z) {
        if (entity == null || !Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(x, v.getY(), z));
    }

    /**
     * Launch entity upward (rocket / jump pad).
     */
    public static void launchUp(Entity entity, double verticalSpeed) {
        if (entity == null || !Double.isFinite(verticalSpeed)) {
            return;
        }
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(v.getX() * 0.5, verticalSpeed, v.getZ() * 0.5));
    }

    /**
     * Zero out velocity.
     */
    public static void stop(Entity entity) {
        if (entity != null) {
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    /**
     * Scale current velocity.
     */
    public static void scaleVelocity(Entity entity, double factor) {
        if (entity == null || !Double.isFinite(factor)) {
            return;
        }
        entity.setVelocity(entity.getVelocity().multiply(factor));
    }

    /**
     * Apply a spherical impulse from a center location (explosion force).
     */
    public static void explosionImpulse(Entity entity, org.bukkit.Location center, double force, double verticalBoost) {
        if (entity == null || center == null || center.getWorld() == null
                || !entity.getWorld().equals(center.getWorld())
                || !Double.isFinite(force) || !Double.isFinite(verticalBoost)) {
            return;
        }
        Vector dir = entity.getLocation().toVector().subtract(center.toVector());
        double distance = dir.length();
        if (distance < 0.1) {
            dir = new Vector(0, 1, 0);
        } else {
            dir = dir.normalize();
        }
        // falloff
        double magnitude = force / (1.0 + distance * 0.35);
        dir.multiply(magnitude);
        dir.setY(dir.getY() + verticalBoost);
        entity.setVelocity(entity.getVelocity().add(dir));
    }

    /**
     * Vector between two entities as normalized direction.
     */
    public static Vector directionBetween(Entity from, Entity to) {
        if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return new Vector();
        }
        Vector dir = to.getLocation().toVector().subtract(from.getLocation().toVector());
        return dir.lengthSquared() < 1.0e-9 ? new Vector() : dir.normalize();
    }

    /**
     * Perpendicular vector (for side-step / orbital movement).
     */
    public static Vector perpendicular(Vector dir) {
        if (dir == null) {
            return new Vector(0, 0, 1);
        }
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
        // 竖直向量没有唯一的水平垂直方向，回退为 +Z
        return perp.lengthSquared() < 1.0e-9 ? new Vector(0, 0, 1) : perp.normalize();
    }

    /**
     * Rotate a vector around Y axis by degrees.
     */
    public static Vector rotateY(Vector vector, double degrees) {
        if (vector == null || !Double.isFinite(degrees)) {
            return new Vector();
        }
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    /**
     * Gravity compensation: upward boost for projectile arcs.
     */
    public static double gravityCompensation(double gravityPerTick, double travelTicks) {
        if (!Double.isFinite(gravityPerTick) || !Double.isFinite(travelTicks)) {
            return 0.0;
        }
        return gravityPerTick * travelTicks * 0.5;
    }

    private static boolean finite(Vector v) {
        return Double.isFinite(v.getX()) && Double.isFinite(v.getY()) && Double.isFinite(v.getZ());
    }
}
