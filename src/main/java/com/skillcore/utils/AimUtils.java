package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Aiming / targeting toolkit (瞄准).
 * <p>
 * Raycast, cone targeting, nearest enemy, crosshair lock-on,
 * prediction aim (lead target), line-of-sight checks.
 */
public final class AimUtils {

    private AimUtils() {
    }

    // ------------------------------------------------------------------
    // Raycast
    // ------------------------------------------------------------------

    /**
     * Raycast from eye location along view direction.
     *
     * @param maxDistance max reach
     * @return first living entity hit, or null
     */
    public static LivingEntity raycastEntity(Player player, double maxDistance) {
        return raycastEntity(player, maxDistance, e -> e instanceof LivingEntity living
                && !living.equals(player)
                && TargetFilter.isAttackable(player, living));
    }

    public static LivingEntity raycastEntity(Player player, double maxDistance, Predicate<Entity> filter) {
        if (player == null) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                maxDistance,
                0.3,
                filter
        );
        if (result == null || result.getHitEntity() == null) {
            return null;
        }
        return result.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    /**
     * Raycast for any entity including blocks. Returns hit entity or null.
     */
    public static Entity raycastAny(Player player, double maxDistance) {
        if (player == null) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                maxDistance,
                0.2
        );
        return result == null ? null : result.getHitEntity();
    }

    /**
     * Raytrace blocks only.
     */
    public static Location raycastBlock(Player player, double maxDistance) {
        if (player == null) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                maxDistance
        );
        return result == null ? null : result.getHitPosition().toLocation(player.getWorld());
    }

    /**
     * Get target at crosshair (entity preferred, else block).
     */
    public static Location getAimLocation(Player player, double maxDistance) {
        LivingEntity entity = raycastEntity(player, maxDistance);
        if (entity != null) {
            return entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
        }
        Location blockHit = raycastBlock(player, maxDistance);
        return blockHit != null ? blockHit : player.getEyeLocation().add(player.getLocation().getDirection().multiply(maxDistance));
    }

    // ------------------------------------------------------------------
    // Cone / area aim
    // ------------------------------------------------------------------

    /**
     * Find living entities inside a cone from the eye.
     *
     * @param angleDegrees half-angle of the cone (0-180)
     * @param maxDistance  range
     */
    public static List<LivingEntity> getConeTargets(LivingEntity caster, double angleDegrees, double maxDistance) {
        return getConeTargets(caster, angleDegrees, maxDistance, e -> true);
    }

    public static List<LivingEntity> getConeTargets(
            LivingEntity caster,
            double angleDegrees,
            double maxDistance,
            Predicate<LivingEntity> filter
    ) {
        List<LivingEntity> result = new ArrayList<>();
        if (caster == null || caster.getWorld() == null) {
            return result;
        }
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        double cosLimit = Math.cos(Math.toRadians(Math.max(0.1, Math.min(180.0, angleDegrees))));

        // 用包围盒先缩小候选集，避免遍历全图实体
        for (Entity entity : caster.getWorld().getNearbyEntities(eye, maxDistance, maxDistance, maxDistance)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.equals(caster) || !DamageUtils.isAlive(living)) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            Vector toTarget = living.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance > maxDistance || distance < 1.0e-6) {
                continue;
            }
            double dot = toTarget.normalize().dot(forward);
            if (dot >= cosLimit) {
                result.add(living);
            }
        }
        return result;
    }

    /**
     * Nearest living entity in a direction / cone (best aim assist target).
     */
    public static LivingEntity getBestConeTarget(LivingEntity caster, double angleDegrees, double maxDistance) {
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        double cosLimit = Math.cos(Math.toRadians(Math.max(0.1, Math.min(180.0, angleDegrees))));

        for (LivingEntity entity : getConeTargets(caster, angleDegrees, maxDistance)) {
            Vector toTarget = entity.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            double dot = toTarget.normalize().dot(forward);
            if (dot < cosLimit) {
                continue;
            }
            // score: prefer center-of-view and closer
            double score = dot * 2.0 - (distance / Math.max(1.0, maxDistance));
            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /**
     * Nearest living entity within radius.
     */
    public static LivingEntity getNearest(LivingEntity origin, double radius) {
        return getNearest(origin, radius, e -> true);
    }

    public static LivingEntity getNearest(LivingEntity origin, double radius, Predicate<LivingEntity> filter) {
        if (origin == null) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDist = radius * radius;
        for (LivingEntity entity : origin.getWorld().getLivingEntities()) {
            if (entity.equals(origin) || !DamageUtils.isAlive(entity)) {
                continue;
            }
            if (filter != null && !filter.test(entity)) {
                continue;
            }
            double distSq = entity.getLocation().distanceSquared(origin.getLocation());
            if (distSq <= nearestDist) {
                nearestDist = distSq;
                nearest = entity;
            }
        }
        return nearest;
    }

    /**
     * All living entities within radius of a location.
     */
    public static List<LivingEntity> getNearby(LivingEntity origin, double radius) {
        return getNearby(origin, radius, e -> true);
    }

    public static List<LivingEntity> getNearby(LivingEntity origin, double radius, Predicate<LivingEntity> filter) {
        List<LivingEntity> list = new ArrayList<>();
        if (origin == null || radius <= 0) {
            return list;
        }
        Collection<Entity> nearby = origin.getNearbyEntities(radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity living
                    && DamageUtils.isAlive(living)
                    && (filter == null || filter.test(living))) {
                if (living.getLocation().distanceSquared(origin.getLocation()) <= radius * radius) {
                    list.add(living);
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // Line of sight
    // ------------------------------------------------------------------

    /**
     * Whether caster has clear line of sight to target.
     */
    public static boolean hasLineOfSight(LivingEntity caster, LivingEntity target) {
        return caster != null && target != null && caster.hasLineOfSight(target);
    }

    public static boolean hasLineOfSight(LivingEntity caster, Location location) {
        return caster != null && location != null && caster.hasLineOfSight(location);
    }

    // ------------------------------------------------------------------
    // Prediction / lead aim (projectile)
    // ------------------------------------------------------------------

    /**
     * Predict where a moving target will be after {@code seconds}.
     * Simple linear velocity prediction.
     */
    public static Location predictLocation(LivingEntity target, double seconds) {
        if (target == null) {
            return null;
        }
        Location loc = target.getLocation();
        Vector velocity = target.getVelocity();
        return loc.add(velocity.clone().multiply(seconds * 20.0 * 0.05 * 20)); // velocity is blocks/tick
    }

    /**
     * Compute launch velocity for a projectile to hit a moving target.
     * Uses simple linear prediction + gravity compensation.
     *
     * @param projectileSpeed desired horizontal speed (blocks/tick)
     * @return launch vector, or null if target invalid
     */
    public static Vector computeLeadVelocity(LivingEntity shooter, LivingEntity target, double projectileSpeed, double gravityCompensation) {
        if (shooter == null || target == null || projectileSpeed <= 0) {
            return null;
        }
        Location from = shooter.getEyeLocation();
        Location to = target.getEyeLocation();
        double distance = from.distance(to);
        double travelTicks = distance / projectileSpeed;

        Vector targetVel = target.getVelocity();
        Location predicted = to.clone().add(targetVel.multiply(travelTicks * 0.05 * 20.0 / 20.0 * 20)); // blocks/tick * ticks
        // simpler: velocity is blocks/tick, travel time in ticks = distance/speed
        predicted = to.clone().add(targetVel.clone().multiply(travelTicks));

        Vector dir = predicted.toVector().subtract(from.toVector());
        dir.setY(dir.getY() + gravityCompensation * travelTicks * 0.5);
        if (dir.lengthSquared() < 1.0e-6) {
            return null;
        }
        return dir.normalize().multiply(projectileSpeed);
    }

    /**
     * Face location smoothly (set yaw/pitch toward target).
     */
    public static void faceLocation(LivingEntity entity, Location target) {
        if (entity == null || target == null) {
            return;
        }
        Location loc = entity.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dir = dir.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(-Math.asin(dir.getY()));
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        entity.teleport(loc);
    }

    // ------------------------------------------------------------------
    // Lock-on
    // ------------------------------------------------------------------

    /**
     * Soft aim assist: if player is looking near an entity, snap return to it.
     *
     * @param assistAngle max angle off-center to assist
     */
    public static LivingEntity aimAssist(Player player, double maxDistance, double assistAngle) {
        LivingEntity direct = raycastEntity(player, maxDistance);
        if (direct != null) {
            return direct;
        }
        return getBestConeTarget(player, assistAngle, maxDistance);
    }

    /**
     * Whether target is within the aim cone of caster.
     */
    public static boolean isInCone(LivingEntity caster, LivingEntity target, double angleDegrees) {
        if (caster == null || target == null) {
            return false;
        }
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 1.0e-6) {
            return true;
        }
        double dot = toTarget.normalize().dot(forward);
        return dot >= Math.cos(Math.toRadians(Math.max(0.1, Math.min(180.0, angleDegrees))));
    }

    /**
     * Spawn a small aim marker particle at a location (for lock-on visuals).
     */
    public static void showAimMarker(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        world.spawnParticle(Particle.CRIT, location, 8, 0.1, 0.1, 0.1, 0.02);
    }

    /**
     * Distance between entity eye locations.
     */
    public static double eyeDistance(LivingEntity a, LivingEntity b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        return a.getEyeLocation().distance(b.getEyeLocation());
    }

    /**
     * Bounding-box based precise raycast hit test.
     */
    public static boolean rayHitsEntity(Location from, Vector direction, LivingEntity entity, double maxDistance) {
        if (from == null || direction == null || entity == null) {
            return false;
        }
        BoundingBox box = entity.getBoundingBox().expand(0.1);
        RayTraceResult result = box.rayTrace(from.toVector(), direction.clone().normalize(), maxDistance);
        return result != null;
    }
}
