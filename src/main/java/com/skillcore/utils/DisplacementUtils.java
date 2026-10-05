package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Displacement / mobility skill toolkit (位移技能).
 * <p>
 * Dash, blink, leap, pull, push, swap, charge, grapple-style pull.
 */
public final class DisplacementUtils {

    private DisplacementUtils() {
    }

    // ------------------------------------------------------------------
    // Dash / blink
    // ------------------------------------------------------------------

    /**
     * Dash entity in its look direction (or custom direction).
     *
     * @param speed    blocks per tick-ish velocity magnitude
     * @param yOffset  vertical boost
     */
    public static void dash(LivingEntity entity, double speed, double yOffset) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        Vector dir = entity.getLocation().getDirection().normalize();
        dash(entity, dir, speed, yOffset);
    }

    public static void dash(LivingEntity entity, Vector direction, double speed, double yOffset) {
        if (entity == null || !entity.isValid() || direction == null) {
            return;
        }
        Vector velocity = direction.clone().normalize().multiply(speed);
        velocity.setY(velocity.getY() + yOffset);
        entity.setVelocity(velocity);
    }

    /**
     * Dash toward a location (directional, full speed).
     */
    public static void dashTo(LivingEntity entity, Location target, double speed) {
        if (entity == null || target == null) {
            return;
        }
        Vector dir = target.toVector().subtract(entity.getLocation().toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dash(entity, dir.normalize(), speed, 0);
    }

    /**
     * Instant blink / teleport with small ender particles.
     *
     * @param maxDistance max teleport range, {@code <= 0} = unlimited
     * @return actual location teleported to, or null if blocked
     */
    public static Location blink(LivingEntity entity, Location destination, double maxDistance) {
        if (entity == null || destination == null) {
            return null;
        }
        Location from = entity.getLocation();
        if (maxDistance > 0 && from.distance(destination) > maxDistance) {
            Vector dir = destination.toVector().subtract(from.toVector()).normalize().multiply(maxDistance);
            destination = from.clone().add(dir);
        }
        Location safe = findSafeLocation(destination, 3);
        if (safe == null) {
            return null;
        }
        safe.setDirection(from.getDirection());
        if (!entity.teleport(safe)) {
            return null;
        }
        playBlinkEffect(from);
        playBlinkEffect(safe);
        return safe;
    }

    /**
     * Blink forward in look direction.
     */
    public static Location blinkForward(LivingEntity entity, double distance) {
        Location dest = entity.getEyeLocation().add(entity.getLocation().getDirection().normalize().multiply(distance));
        return blink(entity, dest, distance + 1);
    }

    // ------------------------------------------------------------------
    // Leap / jump
    // ------------------------------------------------------------------

    /**
     * Leap toward a direction with arc.
     *
     * @param horizontal horizontal speed
     * @param vertical   upward velocity
     */
    public static void leap(LivingEntity entity, double horizontal, double vertical) {
        if (entity == null) {
            return;
        }
        Vector dir = entity.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() > 1.0e-6) {
            dir = dir.normalize().multiply(horizontal);
        } else {
            dir = new Vector(0, 0, 0);
        }
        dir.setY(vertical);
        entity.setVelocity(dir);
    }

    /**
     * Leap toward a target location with simple ballistic horizontal speed.
     */
    public static void leapTo(LivingEntity entity, Location target, double durationSeconds) {
        if (entity == null || target == null || durationSeconds <= 0) {
            return;
        }
        Location from = entity.getLocation();
        double distance = from.distance(target);
        double horizontal = distance / (durationSeconds * 20.0); // per tick
        // approximate velocity vector
        Vector delta = target.toVector().subtract(from.toVector());
        delta.multiply(1.0 / (durationSeconds * 20.0));
        // add gravity compensation for arc
        delta.setY(delta.getY() + (0.08 * durationSeconds * 20.0) * 0.5);
        entity.setVelocity(delta);
    }

    // ------------------------------------------------------------------
    // Push / pull / knockback
    // ------------------------------------------------------------------

    /**
     * Knock target away from a source location.
     */
    public static void knockback(LivingEntity target, Location from, double strength, double vertical) {
        if (target == null || from == null || strength <= 0) {
            return;
        }
        Vector dir = target.getLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            dir = target.getLocation().getDirection().multiply(-1);
        }
        dir = dir.normalize().multiply(strength);
        dir.setY(vertical);
        target.setVelocity(dir);
    }

    /**
     * Knock target away from an entity.
     */
    public static void knockbackFrom(LivingEntity target, LivingEntity source, double strength, double vertical) {
        if (source == null) {
            return;
        }
        knockback(target, source.getLocation(), strength, vertical);
    }

    /**
     * Vanilla-style knockback API.
     */
    public static void vanillaKnockback(LivingEntity target, double strength, double yawDegrees) {
        if (target == null || strength <= 0) {
            return;
        }
        target.knockback(strength, Math.sin(Math.toRadians(yawDegrees)), Math.cos(Math.toRadians(yawDegrees)));
    }

    /**
     * Pull target toward a location (grapple / attract).
     */
    public static void pull(LivingEntity target, Location to, double strength) {
        if (target == null || to == null || strength <= 0) {
            return;
        }
        Vector dir = to.toVector().subtract(target.getLocation().toVector());
        double distance = dir.length();
        if (distance < 0.1) {
            return;
        }
        // scale so far targets don't get insane speed; cap strength
        double mag = Math.min(strength, distance * 0.5 + strength * 0.2);
        target.setVelocity(dir.normalize().multiply(mag).setY(Math.min(0.4, dir.getY() * 0.2 + 0.1)));
    }

    /**
     * Pull target toward an entity.
     */
    public static void pullToEntity(LivingEntity target, LivingEntity puller, double strength) {
        if (puller == null) {
            return;
        }
        pull(target, puller.getLocation(), strength);
    }

    /**
     * Force-set velocity with length clamp.
     */
    public static void setVelocityClamped(Entity entity, Vector velocity, double maxSpeed) {
        if (entity == null || velocity == null) {
            return;
        }
        Vector v = velocity.clone();
        if (maxSpeed > 0 && v.length() > maxSpeed) {
            v = v.normalize().multiply(maxSpeed);
        }
        entity.setVelocity(v);
    }

    // ------------------------------------------------------------------
    // Swap / teleport helpers
    // ------------------------------------------------------------------

    /**
     * Swap positions of two entities.
     */
    public static boolean swapPositions(LivingEntity a, LivingEntity b) {
        if (a == null || b == null) {
            return false;
        }
        Location locA = a.getLocation().clone();
        Location locB = b.getLocation().clone();
        locA.setDirection(b.getLocation().getDirection());
        locB.setDirection(a.getLocation().getDirection());
        boolean okA = a.teleport(locB);
        boolean okB = b.teleport(locA);
        return okA && okB;
    }

    /**
     * Teleport entity to look target with safe block search.
     */
    public static boolean teleportSafe(LivingEntity entity, Location destination) {
        Location safe = findSafeLocation(destination, 2);
        if (safe == null) {
            return false;
        }
        safe.setDirection(entity.getLocation().getDirection());
        return entity.teleport(safe);
    }

    /**
     * Charge: apply velocity toward target and optionally damage on arrival.
     * Visual-only charge; collision detection is up to the caller.
     */
    public static void chargeToward(LivingEntity entity, LivingEntity target, double speed) {
        if (entity == null || target == null) {
            return;
        }
        dashTo(entity, target.getEyeLocation(), speed);
    }

    // ------------------------------------------------------------------
    // Safe location
    // ------------------------------------------------------------------

    /**
     * Find a safe standing location near the desired point.
     *
     * @param searchRadius blocks to search downward/sideways
     * @return safe location or null
     */
    public static Location findSafeLocation(Location desired, int searchRadius) {
        if (desired == null || desired.getWorld() == null) {
            return null;
        }
        World world = desired.getWorld();
        int baseX = desired.getBlockX();
        int baseY = desired.getBlockY();
        int baseZ = desired.getBlockZ();

        for (int dy = 0; dy <= searchRadius; dy++) {
            for (int dyNeg = 0; dyNeg <= searchRadius; dyNeg++) {
                int y = baseY + dy;
                if (dyNeg > 0) {
                    y = baseY - dyNeg;
                }
                if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) {
                    continue;
                }
                if (isSafeStanding(world, baseX, y, baseZ)) {
                    return new Location(world, baseX + 0.5, y, baseZ + 0.5, desired.getYaw(), desired.getPitch());
                }
            }
        }
        return null;
    }

    /**
     * Check 2-block high standing space (feet + head free, solid below).
     */
    public static boolean isSafeStanding(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        var below = world.getBlockAt(x, y - 1, z);
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        return below.getType().isSolid()
                && feet.getType().isAir()
                && head.getType().isAir();
    }

    /**
     * Raycast along direction for a solid hit point (simple).
     *
     * @return hit location or end location
     */
    public static Location raycastGround(Location start, Vector direction, double maxDistance) {
        if (start == null || direction == null || start.getWorld() == null) {
            return start;
        }
        Vector dir = direction.clone().normalize();
        World world = start.getWorld();
        double step = 0.25;
        Location current = start.clone();
        for (double d = 0; d < maxDistance; d += step) {
            current = start.clone().add(dir.clone().multiply(d));
            if (!world.getBlockAt(current).getType().isAir()) {
                return current;
            }
        }
        return start.clone().add(dir.multiply(maxDistance));
    }

    // ------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------

    public static void playBlinkEffect(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.PORTAL, location, 20, 0.3, 0.3, 0.3, 0.05);
        location.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
    }

    public static void playDashEffect(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.CLOUD, location, 12, 0.2, 0.1, 0.2, 0.02);
        location.getWorld().playSound(location, Sound.ENTITY_BREEZE_WIND_BURST, 0.5f, 1.5f);
    }

    public static void playKnockbackEffect(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1, 0, 0, 0, 0);
    }
}
