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
        if (entity == null || velocity == null) {
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
        if (entity == null) {
            return;
        }
        Vector v = entity.getVelocity();
        entity.setVelocity(new Vector(x, v.getY(), z));
    }

    /**
     * Launch entity upward (rocket / jump pad).
     */
    public static void launchUp(Entity entity, double verticalSpeed) {
        if (entity == null) {
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
        if (entity != null) {
            entity.setVelocity(entity.getVelocity().multiply(factor));
        }
    }

    /**
     * Apply a spherical impulse from a center location (explosion force).
     */
    public static void explosionImpulse(Entity entity, org.bukkit.Location center, double force, double verticalBoost) {
        if (entity == null || center == null) {
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
        if (from == null || to == null) {
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
            return new Vector();
        }
        return new Vector(-dir.getZ(), 0, dir.getX()).normalize();
    }

    /**
     * Rotate a vector around Y axis by degrees.
     */
    public static Vector rotateY(Vector vector, double degrees) {
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
        return gravityPerTick * travelTicks * 0.5;
    }
}
