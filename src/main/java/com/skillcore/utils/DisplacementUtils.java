package com.skillcore.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * 位移技能工具 — 突刺、闪现、跳跃、击退、吸附、交换位置。
 * <p>
 * 所有速度参数都会做有限性校验（Bukkit 的 {@code setVelocity} 遇到 NaN/Infinity 会抛异常），
 * 跨世界的位移直接拒绝。
 */
public final class DisplacementUtils {

    private DisplacementUtils() {
    }

    private static boolean finite(double v) {
        return Double.isFinite(v);
    }

    /** 同一世界才允许位移。 */
    private static boolean sameWorld(Entity entity, Location loc) {
        return entity.getWorld() != null && loc.getWorld() != null && entity.getWorld().equals(loc.getWorld());
    }

    // ------------------------------------------------------------------
    // 突刺 / 闪现
    // ------------------------------------------------------------------

    /** 沿实体朝向突刺。 */
    public static void dash(LivingEntity entity, double speed, double yOffset) {
        if (entity == null || !entity.isValid()) {
            return;
        }
        Vector dir = entity.getLocation().getDirection();
        dash(entity, dir, speed, yOffset);
    }

    /**
     * 沿指定方向突刺。
     *
     * @param speed   速度大小（格/tick）
     * @param yOffset 额外的垂直速度
     */
    public static void dash(LivingEntity entity, Vector direction, double speed, double yOffset) {
        if (entity == null || !entity.isValid() || direction == null) {
            return;
        }
        if (!finite(speed) || !finite(yOffset) || speed <= 0) {
            return;
        }
        Vector dir = direction.clone();
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-8) {
            return;
        }
        Vector velocity = dir.normalize().multiply(Math.min(speed, 10.0));
        velocity.setY(Math.max(-5.0, Math.min(5.0, yOffset)));
        entity.setVelocity(velocity);
    }

    /** 朝某个位置突刺（方向取水平分量）。 */
    public static void dashTo(LivingEntity entity, Location target, double speed) {
        if (entity == null || target == null || !sameWorld(entity, target)) {
            return;
        }
        Vector dir = target.toVector().subtract(entity.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) {
            return;
        }
        dash(entity, dir, speed, 0);
    }

    /**
     * 瞬移（闪现），带落点安全搜索。
     *
     * @param maxDistance 最大距离，{@code <= 0} 表示不限制
     * @return 实际落点；被阻挡时返回 null
     */
    public static Location blink(LivingEntity entity, Location destination, double maxDistance) {
        if (entity == null || destination == null || destination.getWorld() == null) {
            return null;
        }
        Location from = entity.getLocation();
        if (!sameWorld(entity, destination)) {
            return null;
        }
        double limit = finite(maxDistance) ? maxDistance : 0.0;
        if (limit > 0) {
            double dist = from.distance(destination);
            if (dist > limit) {
                Vector dir = destination.toVector().subtract(from.toVector());
                if (dir.lengthSquared() < 1.0e-8) {
                    return null;
                }
                destination = from.clone().add(dir.normalize().multiply(limit));
            }
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

    /** 朝视线方向闪现。 */
    public static Location blinkForward(LivingEntity entity, double distance) {
        if (entity == null || !finite(distance) || distance <= 0) {
            return null;
        }
        Location dest = entity.getEyeLocation()
                .add(entity.getLocation().getDirection().normalize().multiply(Math.min(distance, 64.0)));
        return blink(entity, dest, distance + 1);
    }

    // ------------------------------------------------------------------
    // 跳跃
    // ------------------------------------------------------------------

    /**
     * 向前跳跃。
     *
     * @param horizontal 水平速度
     * @param vertical   垂直速度
     */
    public static void leap(LivingEntity entity, double horizontal, double vertical) {
        if (entity == null || !finite(horizontal) || !finite(vertical)) {
            return;
        }
        Vector dir = entity.getLocation().getDirection();
        dir.setY(0);
        if (dir.lengthSquared() > 1.0e-6) {
            dir = dir.normalize().multiply(Math.max(0.0, Math.min(horizontal, 10.0)));
        } else {
            dir = new Vector(0, 0, 0);
        }
        dir.setY(Math.max(-5.0, Math.min(5.0, vertical)));
        entity.setVelocity(dir);
    }

    /** 抛物线跳向目标位置。 */
    public static void leapTo(LivingEntity entity, Location target, double durationSeconds) {
        if (entity == null || target == null || !sameWorld(entity, target)
                || !finite(durationSeconds) || durationSeconds <= 0) {
            return;
        }
        Location from = entity.getLocation();
        double ticks = durationSeconds * 20.0;
        Vector delta = target.toVector().subtract(from.toVector());
        delta.multiply(1.0 / ticks);
        // 粗略重力补偿：MC 重力约 0.08 格/tick²
        delta.setY(delta.getY() + (0.08 * ticks) * 0.5);
        setVelocityClamped(entity, delta, 3.0);
    }

    // ------------------------------------------------------------------
    // 击退 / 吸附
    // ------------------------------------------------------------------

    /** 把目标从某个位置击退。 */
    public static void knockback(LivingEntity target, Location from, double strength, double vertical) {
        if (target == null || from == null || !finite(strength) || !finite(vertical)
                || strength <= 0 || !sameWorld(target, from)) {
            return;
        }
        Vector dir = target.getLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0e-6) {
            dir = target.getLocation().getDirection().multiply(-1);
        }
        if (dir.lengthSquared() < 1.0e-8) {
            dir = new Vector(0, 0, 1);
        }
        dir = dir.normalize().multiply(Math.min(strength, 10.0));
        dir.setY(Math.max(-5.0, Math.min(5.0, vertical)));
        target.setVelocity(dir);
    }

    /** 把目标从某个实体处击退。 */
    public static void knockbackFrom(LivingEntity target, LivingEntity source, double strength, double vertical) {
        if (source == null) {
            return;
        }
        knockback(target, source.getLocation(), strength, vertical);
    }

    /** 原版风格击退（带水平角度）。 */
    public static void vanillaKnockback(LivingEntity target, double strength, double yawDegrees) {
        if (target == null || !finite(strength) || !finite(yawDegrees) || strength <= 0) {
            return;
        }
        target.knockback(Math.min(strength, 10.0),
                Math.sin(Math.toRadians(yawDegrees)), Math.cos(Math.toRadians(yawDegrees)));
    }

    /** 把目标吸向某个位置（抓取/吸引）。 */
    public static void pull(LivingEntity target, Location to, double strength) {
        if (target == null || to == null || !finite(strength) || strength <= 0 || !sameWorld(target, to)) {
            return;
        }
        Vector dir = to.toVector().subtract(target.getLocation().toVector());
        double distance = dir.length();
        if (distance < 0.1) {
            return;
        }
        // 远处目标限制速度，避免瞬移感；竖直方向单独收敛
        double mag = Math.min(Math.min(strength, 10.0), distance * 0.5 + strength * 0.2);
        Vector velocity = dir.normalize().multiply(mag);
        velocity.setY(Math.max(-0.4, Math.min(0.4, velocity.getY() * 0.2 + 0.1)));
        target.setVelocity(velocity);
    }

    /** 把目标吸向某个实体。 */
    public static void pullToEntity(LivingEntity target, LivingEntity puller, double strength) {
        if (puller == null) {
            return;
        }
        pull(target, puller.getLocation(), strength);
    }

    /** 设置速度并限制最大长度（NaN/Infinity 会被拒绝）。 */
    public static void setVelocityClamped(Entity entity, Vector velocity, double maxSpeed) {
        if (entity == null || velocity == null
                || !finite(velocity.getX()) || !finite(velocity.getY()) || !finite(velocity.getZ())) {
            return;
        }
        Vector v = velocity.clone();
        if (finite(maxSpeed) && maxSpeed > 0 && v.lengthSquared() > maxSpeed * maxSpeed) {
            v = v.normalize().multiply(maxSpeed);
        }
        entity.setVelocity(v);
    }

    // ------------------------------------------------------------------
    // 交换 / 安全传送
    // ------------------------------------------------------------------

    /** 交换两个实体的位置（必须同世界）。 */
    public static boolean swapPositions(LivingEntity a, LivingEntity b) {
        if (a == null || b == null || a.equals(b)
                || a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().equals(b.getWorld())) {
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

    /** 安全传送到目标位置（附近搜索可站立点）。 */
    public static boolean teleportSafe(LivingEntity entity, Location destination) {
        if (entity == null || destination == null || !sameWorld(entity, destination)) {
            return false;
        }
        Location safe = findSafeLocation(destination, 2);
        if (safe == null) {
            return false;
        }
        safe.setDirection(entity.getLocation().getDirection());
        return entity.teleport(safe);
    }

    /** 向目标冲锋（纯速度位移，碰撞与伤害由调用方处理）。 */
    public static void chargeToward(LivingEntity entity, LivingEntity target, double speed) {
        if (entity == null || target == null) {
            return;
        }
        dashTo(entity, target.getEyeLocation(), speed);
    }

    // ------------------------------------------------------------------
    // 落点搜索
    // ------------------------------------------------------------------

    /**
     * 在期望位置附近寻找可站立的落点（先原地，再向上/向下逐格搜索）。
     *
     * @param searchRadius 搜索格数
     * @return 安全落点；找不到返回 null
     */
    public static Location findSafeLocation(Location desired, int searchRadius) {
        if (desired == null || desired.getWorld() == null) {
            return null;
        }
        World world = desired.getWorld();
        int x = desired.getBlockX();
        int z = desired.getBlockZ();
        int baseY = desired.getBlockY();
        int radius = Math.max(0, Math.min(searchRadius, 16));

        for (int offset = 0; offset <= radius; offset++) {
            // 先向上找（跳上台阶），再向下找（落地）
            if (offset == 0) {
                if (isSafeStanding(world, x, baseY, z)) {
                    return standingAt(world, desired, x, baseY, z);
                }
                continue;
            }
            int up = baseY + offset;
            if (up <= world.getMaxHeight() - 2 && isSafeStanding(world, x, up, z)) {
                return standingAt(world, desired, x, up, z);
            }
            int down = baseY - offset;
            if (down >= world.getMinHeight() + 1 && isSafeStanding(world, x, down, z)) {
                return standingAt(world, desired, x, down, z);
            }
        }
        return null;
    }

    private static Location standingAt(World world, Location desired, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5, desired.getYaw(), desired.getPitch());
    }

    /** 检查两格高的站立空间（脚/头为空，下方为固体且非岩浆）。 */
    public static boolean isSafeStanding(World world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        if (y - 1 < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
            return false;
        }
        // 未加载的区块会因 getBlockAt 同步加载，闪现落点直接视为不可用（避免主线程卡顿）
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        var below = world.getBlockAt(x, y - 1, z);
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        return below.getType().isSolid()
                && below.getType() != org.bukkit.Material.LAVA
                && feet.getType().isAir()
                && head.getType().isAir();
    }

    /** 沿方向做简单的方块射线，返回命中点（未命中返回终点）。 */
    public static Location raycastGround(Location start, Vector direction, double maxDistance) {
        if (start == null || direction == null || start.getWorld() == null
                || !finite(maxDistance) || maxDistance <= 0) {
            return start;
        }
        Vector dir = direction.clone();
        if (dir.lengthSquared() < 1.0e-8) {
            return start;
        }
        dir.normalize();
        World world = start.getWorld();
        double step = 0.25;
        for (double d = 0; d < maxDistance; d += step) {
            Location current = start.clone().add(dir.clone().multiply(d));
            if (!world.getBlockAt(current).getType().isAir()) {
                return current;
            }
        }
        return start.clone().add(dir.multiply(maxDistance));
    }

    // ------------------------------------------------------------------
    // 特效
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
