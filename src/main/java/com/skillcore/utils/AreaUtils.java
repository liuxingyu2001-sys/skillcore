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
 * AOE 选怪工具 — 所有范围技能都应经过这里，再配合 {@link TargetFilter} 过滤。
 * <p>
 * 统一使用 {@link World#getNearbyEntities} 包围盒缩小候选集，避免全图遍历。
 */
public final class AreaUtils {

    private AreaUtils() {
    }

    private static boolean badCenter(Location center) {
        return center == null || center.getWorld() == null
                || !Double.isFinite(center.getX())
                || !Double.isFinite(center.getY())
                || !Double.isFinite(center.getZ());
    }

    /** 球形范围内的存活生物。 */
    public static List<LivingEntity> getSphere(Location center, double radius) {
        return getSphere(center, radius, e -> true);
    }

    public static List<LivingEntity> getSphere(Location center, double radius, Predicate<LivingEntity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (badCenter(center) || !Double.isFinite(radius) || radius <= 0) {
            return result;
        }
        double radiusSq = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || !DamageUtils.isAlive(living)) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            if (living.getLocation().distanceSquared(center) <= radiusSq) {
                result.add(living);
            }
        }
        return result;
    }

    /** 圆柱范围（水平半径 + 垂直半高）。 */
    public static List<LivingEntity> getCylinder(Location center, double radius, double halfHeight) {
        return getCylinder(center, radius, halfHeight, e -> true);
    }

    public static List<LivingEntity> getCylinder(Location center, double radius, double halfHeight, Predicate<LivingEntity> filter) {
        List<LivingEntity> result = new ArrayList<>();
        if (badCenter(center) || !Double.isFinite(radius) || radius <= 0) {
            return result;
        }
        double h = Double.isFinite(halfHeight) ? Math.abs(halfHeight) : 0.0;
        if (h <= 0) {
            return result;
        }
        double radiusSq = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, h, radius)) {
            if (!(entity instanceof LivingEntity living) || !DamageUtils.isAlive(living)) {
                continue;
            }
            if (filter != null && !filter.test(living)) {
                continue;
            }
            Location loc = living.getLocation();
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            double dy = loc.getY() - center.getY();
            if (dx * dx + dz * dz <= radiusSq && Math.abs(dy) <= h) {
                result.add(living);
            }
        }
        return result;
    }

    /** 长方体范围（以 center 为中心，各轴半径）。 */
    public static List<LivingEntity> getBox(Location center, double xRadius, double yRadius, double zRadius) {
        List<LivingEntity> result = new ArrayList<>();
        if (badCenter(center)) {
            return result;
        }
        double x = Double.isFinite(xRadius) ? Math.abs(xRadius) : 0.0;
        double y = Double.isFinite(yRadius) ? Math.abs(yRadius) : 0.0;
        double z = Double.isFinite(zRadius) ? Math.abs(zRadius) : 0.0;
        if (x <= 0 && y <= 0 && z <= 0) {
            return result;
        }
        // BoundingBox.of 要求非负半径；全 0 轴会被忽略
        BoundingBox box = BoundingBox.of(center,
                Math.max(x, 0.0), Math.max(y, 0.0), Math.max(z, 0.0));
        for (Entity entity : center.getWorld().getNearbyEntities(center, Math.max(x, 0.0), Math.max(y, 0.0), Math.max(z, 0.0))) {
            if (entity instanceof LivingEntity living && DamageUtils.isAlive(living)
                    && living.getBoundingBox().overlaps(box)) {
                result.add(living);
            }
        }
        return result;
    }

    /** 两点之间、指定宽度的线段范围。 */
    public static List<LivingEntity> getLine(Location from, Location to, double width) {
        List<LivingEntity> result = new ArrayList<>();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) {
            return result;
        }
        // 跨世界没有几何意义，直接返回空，避免 distanceToSegment 产生错误结果
        if (!from.getWorld().equals(to.getWorld())) {
            return result;
        }
        double w = Double.isFinite(width) ? Math.abs(width) : 0.0;
        World world = from.getWorld();
        double minX = Math.min(from.getX(), to.getX()) - w;
        double maxX = Math.max(from.getX(), to.getX()) + w;
        double minY = Math.min(from.getY(), to.getY()) - w;
        double maxY = Math.max(from.getY(), to.getY()) + w;
        double minZ = Math.min(from.getZ(), to.getZ()) - w;
        double maxZ = Math.max(from.getZ(), to.getZ()) + w;
        Location center = new Location(world, (minX + maxX) / 2.0, (minY + maxY) / 2.0, (minZ + maxZ) / 2.0);
        double hx = (maxX - minX) / 2.0;
        double hy = (maxY - minY) / 2.0;
        double hz = (maxZ - minZ) / 2.0;
        for (Entity entity : world.getNearbyEntities(center, hx, hy, hz)) {
            if (!(entity instanceof LivingEntity living) || !DamageUtils.isAlive(living)) {
                continue;
            }
            if (distanceToSegment(living.getLocation(), from, to) <= w) {
                result.add(living);
            }
        }
        return result;
    }

    /** 点到线段的最短距离。 */
    public static double distanceToSegment(Location point, Location a, Location b) {
        if (point == null || a == null || b == null) {
            return Double.MAX_VALUE;
        }
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

    /** 从结果里排除指定实体（如施法者本人 / 队友）。 */
    public static List<LivingEntity> exclude(List<LivingEntity> input, LivingEntity... excluded) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        List<LivingEntity> result = new ArrayList<>(input);
        if (excluded != null) {
            for (LivingEntity e : excluded) {
                if (e != null) {
                    result.remove(e);
                }
            }
        }
        return result;
    }

    /** 只保留玩家。 */
    public static List<Player> playersOnly(List<LivingEntity> input) {
        List<Player> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (LivingEntity e : input) {
            if (e instanceof Player p) {
                result.add(p);
            }
        }
        return result;
    }

    /** 只保留非玩家生物。 */
    public static List<LivingEntity> mobsOnly(List<LivingEntity> input) {
        List<LivingEntity> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (LivingEntity e : input) {
            if (e instanceof Player) {
                continue;
            }
            result.add(e);
        }
        return result;
    }

    /** 水平圆环（甜甜圈）范围。 */
    public static List<LivingEntity> getRing(Location center, double innerRadius, double outerRadius, double halfHeight) {
        if (badCenter(center)) {
            return new ArrayList<>();
        }
        double inner = Double.isFinite(innerRadius) ? Math.max(0.0, innerRadius) : 0.0;
        double outer = Double.isFinite(outerRadius) ? Math.abs(outerRadius) : 0.0;
        if (outer <= 0) {
            return new ArrayList<>();
        }
        // 内径大于外径时交换，避免调用方参数写反导致永远选不到目标
        if (inner > outer) {
            double tmp = inner;
            inner = outer;
            outer = tmp;
        }
        List<LivingEntity> all = getCylinder(center, outer, halfHeight);
        List<LivingEntity> result = new ArrayList<>();
        double innerSq = inner * inner;
        for (LivingEntity e : all) {
            double dx = e.getLocation().getX() - center.getX();
            double dz = e.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz >= innerSq) {
                result.add(e);
            }
        }
        return result;
    }

    /** 圆内随机点（散射类技能用）。 */
    public static Location randomInCircle(Location center, double radius) {
        if (center == null) {
            return null;
        }
        double r = Double.isFinite(radius) ? Math.max(0.0, radius) : 0.0;
        double angle = Math.random() * Math.PI * 2;
        double dist = Math.sqrt(Math.random()) * r;
        return center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
    }
}
