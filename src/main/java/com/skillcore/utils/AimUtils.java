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
 * 瞄准 / 选怪工具 — 射线、锥形、最近目标、预判、视线检测。
 * <p>
 * 统一使用包围盒 / 射线 API，不做全图实体遍历。
 */
public final class AimUtils {

    private AimUtils() {
    }

    private static boolean goodRange(double range) {
        return Double.isFinite(range) && range > 0;
    }

    // ------------------------------------------------------------------
    // 射线
    // ------------------------------------------------------------------

    /** 从眼睛沿视线射线，返回第一个可攻击生物。 */
    public static LivingEntity raycastEntity(Player player, double maxDistance) {
        return raycastEntity(player, maxDistance, e -> e instanceof LivingEntity living
                && !living.equals(player)
                && TargetFilter.isAttackable(player, living));
    }

    public static LivingEntity raycastEntity(Player player, double maxDistance, Predicate<Entity> filter) {
        if (player == null || !goodRange(maxDistance)) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance,
                0.3,
                filter
        );
        if (result == null || result.getHitEntity() == null) {
            return null;
        }
        return result.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    /** 射线拾取任意实体（含非生物）。 */
    public static Entity raycastAny(Player player, double maxDistance) {
        if (player == null || !goodRange(maxDistance)) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance,
                0.2
        );
        return result == null ? null : result.getHitEntity();
    }

    /** 只对方块做射线，返回命中点。 */
    public static Location raycastBlock(Player player, double maxDistance) {
        if (player == null || !goodRange(maxDistance)) {
            return null;
        }
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                maxDistance
        );
        return result == null || result.getHitPosition() == null
                ? null
                : result.getHitPosition().toLocation(player.getWorld());
    }

    /** 准星位置：优先实体身体中心，其次方块命中点，最后视线尽头。 */
    public static Location getAimLocation(Player player, double maxDistance) {
        if (player == null) {
            return null;
        }
        double range = goodRange(maxDistance) ? maxDistance : 32.0;
        LivingEntity entity = raycastEntity(player, range);
        if (entity != null) {
            return entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
        }
        Location blockHit = raycastBlock(player, range);
        return blockHit != null
                ? blockHit
                : player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
    }

    // ------------------------------------------------------------------
    // 锥形 / 范围
    // ------------------------------------------------------------------

    /** 眼睛前方锥形范围内的生物（默认只返回可攻击目标，过滤友军/宠物/NPC/盔甲架/同队）。 */
    public static List<LivingEntity> getConeTargets(LivingEntity caster, double angleDegrees, double maxDistance) {
        // 注意必须带 TargetFilter：辅助瞄准会直接把结果交给 ctx.damage() 造成单体伤害，
        // 不过滤就能打到队友 / 宠物 / 盔甲架。
        return getConeTargets(caster, angleDegrees, maxDistance, e -> TargetFilter.isAttackable(caster, e));
    }

    public static List<LivingEntity> getConeTargets(
            LivingEntity caster,
            double angleDegrees,
            double maxDistance,
            Predicate<LivingEntity> filter
    ) {
        List<LivingEntity> result = new ArrayList<>();
        if (caster == null || caster.getWorld() == null || !goodRange(maxDistance)) {
            return result;
        }
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection();
        if (forward.lengthSquared() < 1.0e-8) {
            return result;
        }
        forward.normalize();
        double angle = Double.isFinite(angleDegrees) ? Math.max(0.1, Math.min(180.0, angleDegrees)) : 45.0;
        double cosLimit = Math.cos(Math.toRadians(angle));

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
            if (toTarget.normalize().dot(forward) >= cosLimit) {
                result.add(living);
            }
        }
        return result;
    }

    /** 锥形内评分最高的目标（越靠近准星、越近分越高）。 */
    public static LivingEntity getBestConeTarget(LivingEntity caster, double angleDegrees, double maxDistance) {
        if (caster == null || !goodRange(maxDistance)) {
            return null;
        }
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection();
        if (forward.lengthSquared() < 1.0e-8) {
            return null;
        }
        forward.normalize();
        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity entity : getConeTargets(caster, angleDegrees, maxDistance)) {
            Vector toTarget = entity.getEyeLocation().toVector().subtract(eye.toVector());
            double distance = toTarget.length();
            if (distance < 1.0e-6) {
                continue;
            }
            double dot = toTarget.normalize().dot(forward);
            double score = dot * 2.0 - (distance / Math.max(1.0, maxDistance));
            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    /** 半径内最近的生物（包围盒预筛，不做全图扫描；默认只返回可攻击目标）。 */
    public static LivingEntity getNearest(LivingEntity origin, double radius) {
        return getNearest(origin, radius, e -> TargetFilter.isAttackable(origin, e));
    }

    public static LivingEntity getNearest(LivingEntity origin, double radius, Predicate<LivingEntity> filter) {
        if (origin == null || origin.getWorld() == null || !goodRange(radius)) {
            return null;
        }
        LivingEntity nearest = null;
        double nearestDist = radius * radius;
        Location center = origin.getLocation();
        for (Entity entity : origin.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || living.equals(origin) || !DamageUtils.isAlive(living)) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            double distSq = living.getLocation().distanceSquared(center);
            if (distSq <= nearestDist) {
                nearestDist = distSq;
                nearest = living;
            }
        }
        return nearest;
    }

    /** 半径内全部生物。 */
    public static List<LivingEntity> getNearby(LivingEntity origin, double radius) {
        return getNearby(origin, radius, e -> true);
    }

    public static List<LivingEntity> getNearby(LivingEntity origin, double radius, Predicate<LivingEntity> filter) {
        List<LivingEntity> list = new ArrayList<>();
        if (origin == null || origin.getWorld() == null || !goodRange(radius)) {
            return list;
        }
        Location center = origin.getLocation();
        double radiusSq = radius * radius;
        Collection<Entity> nearby = origin.getNearbyEntities(radius, radius, radius);
        for (Entity entity : nearby) {
            if (entity instanceof LivingEntity living
                    && DamageUtils.isAlive(living)
                    && (filter == null || filter.test(living))) {
                if (living.getLocation().distanceSquared(center) <= radiusSq) {
                    list.add(living);
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 视线
    // ------------------------------------------------------------------

    /** 施法者到目标是否有视线。 */
    public static boolean hasLineOfSight(LivingEntity caster, LivingEntity target) {
        return caster != null && target != null && caster.hasLineOfSight(target);
    }

    public static boolean hasLineOfSight(LivingEntity caster, Location location) {
        return caster != null && location != null && caster.hasLineOfSight(location);
    }

    // ------------------------------------------------------------------
    // 预判 / 弹道
    // ------------------------------------------------------------------

    /**
     * 按当前速度线性预判目标在 {@code seconds} 秒后的位置。
     */
    public static Location predictLocation(LivingEntity target, double seconds) {
        if (target == null) {
            return null;
        }
        Location loc = target.getLocation();
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return loc;
        }
        // 速度单位是 格/tick，seconds 秒 = seconds*20 tick
        return loc.add(target.getVelocity().multiply(seconds * 20.0));
    }

    /**
     * 计算命中移动目标所需的发射速度（线性预判 + 重力补偿）。
     *
     * @param projectileSpeed 期望弹速（格/tick）
     * @return 发射向量；目标无效或跨世界时返回 null
     */
    public static Vector computeLeadVelocity(LivingEntity shooter, LivingEntity target,
                                             double projectileSpeed, double gravityCompensation) {
        if (shooter == null || target == null || !goodRange(projectileSpeed)
                || shooter.getWorld() == null || !shooter.getWorld().equals(target.getWorld())) {
            return null;
        }
        Location from = shooter.getEyeLocation();
        Location to = target.getEyeLocation();
        double distance = from.distance(to);
        double travelTicks = distance / projectileSpeed;

        // 目标速度（格/tick）× 飞行 tick 数 = 预判位移
        Location predicted = to.clone().add(target.getVelocity().multiply(travelTicks));

        Vector dir = predicted.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            return null;
        }
        double compensation = Double.isFinite(gravityCompensation) ? gravityCompensation : 0.0;
        dir.setY(dir.getY() + compensation * travelTicks * 0.5);
        return dir.normalize().multiply(projectileSpeed);
    }

    /** 把实体转向目标位置（只改 yaw/pitch）。 */
    public static void faceLocation(LivingEntity entity, Location target) {
        if (entity == null || target == null || target.getWorld() == null
                || !target.getWorld().equals(entity.getWorld())) {
            return;
        }
        Location loc = entity.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dir = dir.normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        float pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, dir.getY()))));
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        entity.teleport(loc);
    }

    // ------------------------------------------------------------------
    // 辅助瞄准
    // ------------------------------------------------------------------

    /**
     * 软辅助瞄准：准星附近有目标时返回该目标。
     *
     * @param assistAngle 允许偏离准星的最大角度
     */
    public static LivingEntity aimAssist(Player player, double maxDistance, double assistAngle) {
        if (player == null) {
            return null;
        }
        LivingEntity direct = raycastEntity(player, maxDistance);
        if (direct != null) {
            return direct;
        }
        return getBestConeTarget(player, assistAngle, maxDistance);
    }

    /** 目标是否在施法者的锥形内。 */
    public static boolean isInCone(LivingEntity caster, LivingEntity target, double angleDegrees) {
        if (caster == null || target == null) {
            return false;
        }
        Location eye = caster.getEyeLocation();
        Vector forward = eye.getDirection();
        if (forward.lengthSquared() < 1.0e-8) {
            return false;
        }
        forward.normalize();
        Vector toTarget = target.getEyeLocation().toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() < 1.0e-6) {
            return true;
        }
        double angle = Double.isFinite(angleDegrees) ? Math.max(0.1, Math.min(180.0, angleDegrees)) : 45.0;
        return toTarget.normalize().dot(forward) >= Math.cos(Math.toRadians(angle));
    }

    /** 在位置生成一个小型瞄准标记粒子。 */
    public static void showAimMarker(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        world.spawnParticle(Particle.CRIT, location, 8, 0.1, 0.1, 0.1, 0.02);
    }

    /** 两个实体眼睛之间的距离（跨世界返回 {@link Double#MAX_VALUE}）。 */
    public static double eyeDistance(LivingEntity a, LivingEntity b) {
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.getEyeLocation().distance(b.getEyeLocation());
    }

    /** 基于碰撞箱的精确射线命中检测。 */
    public static boolean rayHitsEntity(Location from, Vector direction, LivingEntity entity, double maxDistance) {
        if (from == null || direction == null || entity == null || !goodRange(maxDistance)) {
            return false;
        }
        if (direction.lengthSquared() < 1.0e-8) {
            return false;
        }
        BoundingBox box = entity.getBoundingBox().expand(0.1);
        RayTraceResult result = box.rayTrace(from.toVector(), direction.clone().normalize(), maxDistance);
        return result != null;
    }
}
