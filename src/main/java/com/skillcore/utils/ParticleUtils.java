package com.skillcore.utils;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Collection;

/**
 * Particle / visual effect toolkit.
 */
public final class ParticleUtils {

    private ParticleUtils() {
    }

    public static void spawn(World world, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || particle == null || location == null) {
            return;
        }
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawn(Location location, Particle particle, int count) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(particle, location, count, 0.1, 0.1, 0.1, 0);
    }

    /**
     * Draw a circle of particles on the XZ plane.
     */
    public static void circle(Location center, double radius, Particle particle, int points) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location p = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Draw a vertical circle (facing look direction simplified as Y-axis ring).
     */
    public static void ring(Location center, double radius, Particle particle, int points, double yOffset) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        circle(center.clone().add(0, yOffset, 0), radius, particle, points);
    }

    /**
     * Sphere shell of particles.
     */
    public static void sphereShell(Location center, double radius, Particle particle, int points) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);
            world.spawnParticle(particle, center.clone().add(x, y, z), 1, 0, 0, 0, 0);
        }
    }

    /**
     * Line of particles between two locations.
     */
    public static void line(Location from, Location to, Particle particle, double step) {
        if (from == null || to == null || from.getWorld() == null) {
            return;
        }
        World world = from.getWorld();
        double distance = from.distance(to);
        int points = Math.max(1, (int) (distance / Math.max(0.1, step)));
        for (int i = 0; i <= points; i++) {
            Location p = from.clone().add(to.clone().subtract(from).multiply(i / (double) points));
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Helix / spiral effect around a center.
     */
    public static void helix(Location center, double radius, double height, Particle particle, int points) {
        if (center == null || center.getWorld() == null) {
            return;
        }
        World world = center.getWorld();
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = t * Math.PI * 8;
            Location p = center.clone().add(
                    Math.cos(angle) * radius,
                    t * height,
                    Math.sin(angle) * radius
            );
            world.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Colored dust particle (requires DustOptions).
     */
    public static void coloredDust(Location location, Color color, float size, int count) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0.1, 0.1, 0.1, 0, dust);
    }

    /**
     * Expanding ring effect (fireworks / flash style).
     */
    public static void expandingRing(Location center, double maxRadius, Particle particle, int steps, long intervalTicks) {
        // simple immediate version; caller can schedule for animation
        circle(center, maxRadius, particle, steps * 4);
    }

    /**
     * Apply a particle burst on multiple locations.
     */
    public static void burst(Collection<Location> locations, Particle particle, int count) {
        if (locations == null) {
            return;
        }
        for (Location loc : locations) {
            spawn(loc, particle, count);
        }
    }

    /**
     * Simple hit marker at entity body center.
     */
    public static void hitMarker(org.bukkit.entity.LivingEntity entity, Particle particle) {
        if (entity == null) {
            return;
        }
        Location loc = entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
        spawn(loc, particle, 15);
    }

    /**
     * Cast circle under entity (skill cast VFX).
     */
    public static void castCircle(org.bukkit.entity.LivingEntity entity, Particle particle, double radius) {
        if (entity == null) {
            return;
        }
        circle(entity.getLocation(), radius, particle, 24);
    }
}
