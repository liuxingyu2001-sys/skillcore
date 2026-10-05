package com.skillcore.utils;

import com.skillcore.effect.EffectSettings;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 粒子绘制工具类 — 覆盖点/线/环/球/立方/锥/螺旋/光束等常用形状，
 * 并统一走 {@link EffectSettings} 开关、密度与降级粒子。
 * <p>
 * 所有方法对 null / NaN / 无穷坐标做了防护，粒子不受支持时自动降级。
 */
public final class ParticleUtils {

    private ParticleUtils() {
    }

    // ------------------------------------------------------------------
    // 基础：安全生成
    // ------------------------------------------------------------------

    public static void spawn(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (!EffectSettings.particlesEnabled() || world == null || particle == null || location == null) {
            return;
        }
        int n = EffectSettings.scaleCount(count);
        if (n <= 0 || !safe(location)) return;
        try {
            world.spawnParticle(particle, location, n, offsetX, offsetY, offsetZ, extra);
        } catch (Exception ex) {
            world.spawnParticle(fallback(), location, n, offsetX, offsetY, offsetZ, extra);
        }
    }

    public static void spawn(Location location, Particle particle, int count) {
        spawn(location, particle, count, 0.0, 0.0, 0.0, 0.0);
    }

    public static void spawn(Location location, Particle particle, int count,
                             double offsetX, double offsetY, double offsetZ) {
        spawn(location, particle, count, offsetX, offsetY, offsetZ, 0.0);
    }

    public static void spawn(Location location, Particle particle, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (location == null) return;
        spawn(location.getWorld(), particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    /** 带额外数据的粒子（DUST / BLOCK / ITEM 等）。 */
    public static <T> void spawn(Location location, Particle particle, int count,
                                 double offsetX, double offsetY, double offsetZ,
                                 double extra, T data) {
        if (!EffectSettings.particlesEnabled() || location == null || location.getWorld() == null
                || particle == null || data == null || !safe(location)) {
            return;
        }
        int n = EffectSettings.scaleCount(count);
        if (n <= 0) return;
        try {
            location.getWorld().spawnParticle(particle, location, n, offsetX, offsetY, offsetZ, extra, data);
        } catch (Exception ex) {
            location.getWorld().spawnParticle(fallback(), location, n, offsetX, offsetY, offsetZ, extra);
        }
    }

    /** 向若干个位置批量生成粒子。 */
    public static void spawnAll(Collection<Location> locations, Particle particle, int count) {
        if (locations == null) return;
        for (Location loc : locations) {
            spawn(loc, particle, count);
        }
    }

    // ------------------------------------------------------------------
    // 基础形状
    // ------------------------------------------------------------------

    /** XZ 平面圆环。 */
    public static void circle(Location center, double radius, Particle particle, int points) {
        ring(center, radius, particle, points, new Vector(0, 1, 0));
    }

    /** 指定法线方向的圆环。 */
    public static void ring(Location center, double radius, Particle particle, int points, Vector axis) {
        if (!EffectSettings.particlesEnabled() || center == null || center.getWorld() == null
                || radius <= 0 || particle == null) {
            return;
        }
        Vector a = (axis == null ? new Vector(0, 1, 0) : axis.clone()).normalize();
        Vector u = perpendicular(a);
        Vector v = a.clone().crossProduct(u).normalize();
        int n = Math.max(3, points);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            Vector offset = u.clone().multiply(Math.cos(angle) * radius)
                    .add(v.clone().multiply(Math.sin(angle) * radius));
            spawn(center.clone().add(offset), particle, 1);
        }
    }

    /** 竖直圆环（面向观察方向）。 */
    public static void ring(Location center, double radius, Particle particle, int points, double yOffset) {
        ring(center.clone().add(0, yOffset, 0), radius, particle, points, new Vector(0, 1, 0));
    }

    /** 球壳。 */
    public static void sphereShell(Location center, double radius, Particle particle, int points) {
        if (!EffectSettings.particlesEnabled() || center == null || radius <= 0) return;
        int n = Math.max(1, points);
        for (int i = 0; i < n; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);
            double x = radius * Math.sin(phi) * Math.cos(theta);
            double y = radius * Math.sin(phi) * Math.sin(theta);
            double z = radius * Math.cos(phi);
            spawn(center.clone().add(x, y, z), particle, 1);
        }
    }

    /** 实心球（随机点，数量 = points）。 */
    public static void sphere(Location center, double radius, Particle particle, int points) {
        if (!EffectSettings.particlesEnabled() || center == null || radius <= 0) return;
        for (int i = 0; i < Math.max(1, points); i++) {
            Vector dir = randomUnitVector();
            double r = radius * Math.cbrt(Math.random());
            spawn(center.clone().add(dir.multiply(r)), particle, 1);
        }
    }

    /** 立方体边框。 */
    public static void cube(Location center, double size, Particle particle, int edgePoints) {
        if (!EffectSettings.particlesEnabled() || center == null || size <= 0) return;
        int n = Math.max(2, edgePoints);
        double h = size / 2.0;
        for (int axis = 0; axis < 3; axis++) {
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    double t = -h + (size * a / (n - 1.0));
                    double u = -h + (size * b / (n - 1.0));
                    Vector v = switch (axis) {
                        case 0 -> new Vector(0, t, u);
                        case 1 -> new Vector(t, 0, u);
                        default -> new Vector(t, u, 0);
                    };
                    spawn(center.clone().add(v), particle, 1);
                }
            }
        }
    }

    /** 螺旋线。 */
    public static void helix(Location center, double radius, double height, Particle particle, int points) {
        if (!EffectSettings.particlesEnabled() || center == null || radius <= 0) return;
        int n = Math.max(2, points);
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double angle = t * Math.PI * 8;
            spawn(center.clone().add(
                    Math.cos(angle) * radius,
                    t * height,
                    Math.sin(angle) * radius), particle, 1);
        }
    }

    /** 沿朝向的锥形。 */
    public static void cone(Location tip, Vector direction, double angleDeg, double length,
                            Particle particle, int points) {
        if (!EffectSettings.particlesEnabled() || tip == null || direction == null || length <= 0) return;
        Vector dir = direction.clone().normalize();
        int n = Math.max(2, points);
        double tan = Math.tan(Math.toRadians(Math.max(0, Math.min(89, angleDeg))));
        for (int i = 0; i <= n; i++) {
            double dist = length * i / n;
            double r = tan * dist;
            Location base = tip.clone().add(dir.clone().multiply(dist));
            ring(base, Math.max(0.0, r), particle, Math.max(4, n / 2), dir);
        }
    }

    /** 沿朝向的光束。 */
    public static void beam(Location from, Vector direction, double length, double step, Particle particle) {
        if (!EffectSettings.particlesEnabled() || from == null || direction == null) return;
        Vector dir = direction.clone().normalize();
        double s = Math.max(0.05, step);
        for (double d = 0; d <= length; d += s) {
            spawn(from.clone().add(dir.clone().multiply(d)), particle, 1);
        }
    }

    /** 两点之间连线。 */
    public static void line(Location from, Location to, Particle particle, double step) {
        if (!EffectSettings.particlesEnabled() || from == null || to == null || from.getWorld() == null) {
            return;
        }
        double distance = from.distance(to);
        int points = Math.max(1, (int) (distance / Math.max(0.1, step)));
        for (int i = 0; i <= points; i++) {
            Location p = from.clone().add(to.clone().subtract(from).multiply(i / (double) points));
            spawn(p, particle, 1);
        }
    }

    // ------------------------------------------------------------------
    // 闪电
    // ------------------------------------------------------------------

    /**
     * 计算闪电折线的路径点（中点位移法，中间抖动更大）。
     *
     * @return 从 from 到 to 的折线点列表（含两端）
     */
    public static List<Location> boltPoints(Location from, Location to, double jaggedness, int segments) {
        List<Location> points = new ArrayList<>();
        if (from == null || to == null || from.getWorld() == null) {
            return points;
        }
        points.add(from.clone());
        double len = from.distance(to);
        if (len < 1.0e-6 || segments < 2) {
            points.add(to.clone());
            return points;
        }
        Vector step = to.toVector().subtract(from.toVector()).normalize();
        Vector perp = perpendicular(step);
        int n = Math.max(2, segments);
        for (int i = 1; i < n; i++) {
            double t = i / (double) n;
            // 中间抖动最大，两端贴近端点
            double envelope = Math.sin(Math.PI * t);
            double offset = (Math.random() * 2 - 1) * jaggedness * len / n * envelope * 4;
            points.add(from.clone()
                    .add(step.clone().multiply(t * len))
                    .add(perp.clone().multiply(offset)));
        }
        points.add(to.clone());
        return points;
    }

    /** 沿折线生成普通粒子闪电。 */
    public static void bolt(Location from, Location to, Particle particle, double jaggedness, int segments) {
        for (Location p : boltPoints(from, to, jaggedness, segments)) {
            spawn(p, particle, 1);
        }
    }

    /** 沿折线生成彩色尘 + 电光核心的闪电。 */
    public static void boltDust(Location from, Location to, Color color, float size, int dustCount,
                                double jaggedness, int segments) {
        for (Location p : boltPoints(from, to, jaggedness, segments)) {
            dust(p, color, size, dustCount);
            spawn(p, Particle.ELECTRIC_SPARK, 1);
        }
    }

    /** 多边形边框。 */
    public static void polygon(Location center, double radius, int sides, Particle particle) {
        if (!EffectSettings.particlesEnabled() || center == null || radius <= 0) return;
        int n = Math.max(3, sides);
        Location prev = null;
        for (int i = 0; i <= n; i++) {
            double angle = 2 * Math.PI * i / n;
            Location p = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            if (prev != null) {
                line(prev, p, particle, 0.15);
            }
            prev = p;
        }
    }

    /** 星形。 */
    public static void star(Location center, double radius, int points, Particle particle) {
        if (!EffectSettings.particlesEnabled() || center == null || radius <= 0) return;
        int n = Math.max(2, points);
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            Location tip = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            line(center, tip, particle, 0.2);
        }
    }

    /** 抛物线（用于投掷物轨迹）。 */
    public static void parabola(Location start, Vector velocity, double gravity, int steps, Particle particle) {
        if (!EffectSettings.particlesEnabled() || start == null || velocity == null) return;
        Location p = start.clone();
        Vector v = velocity.clone();
        for (int i = 0; i < steps; i++) {
            spawn(p, particle, 1);
            p = p.clone().add(v);
            v = v.clone().setY(v.getY() - gravity);
        }
    }

    /** 随机圆内点。 */
    public static Location randomInCircle(Location center, double radius) {
        double angle = Math.random() * Math.PI * 2;
        double r = Math.sqrt(Math.random()) * Math.max(0, radius);
        return center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }

    /** 随机球内点。 */
    public static Location randomInSphere(Location center, double radius) {
        Vector dir = randomUnitVector();
        double r = radius * Math.cbrt(Math.random());
        return center.clone().add(dir.multiply(Math.max(0, r)));
    }

    // ------------------------------------------------------------------
    // 特殊粒子
    // ------------------------------------------------------------------

    /** 彩色尘。 */
    public static void dust(Location location, Color color, float size, int count) {
        if (color == null) return;
        spawn(location, Particle.DUST, count, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(color, size));
    }

    /** 双色过渡尘。 */
    public static void dustTransition(Location location, Color from, Color to, float size, int count) {
        if (from == null || to == null) return;
        spawn(location, Particle.DUST_COLOR_TRANSITION, count, 0.1, 0.1, 0.1, 0,
                new Particle.DustTransition(from, to, size));
    }

    /** 方块碎屑。 */
    public static void blockDust(Location location, BlockData data, int count) {
        if (data == null) return;
        spawn(location, Particle.BLOCK, count, 0.2, 0.2, 0.2, 0, data);
    }

    /** 物品粒子。 */
    public static void itemParticle(Location location, ItemStack item, int count) {
        if (item == null) return;
        spawn(location, Particle.ITEM, count, 0.2, 0.2, 0.2, 0, item);
    }

    // ------------------------------------------------------------------
    // 语义化效果
    // ------------------------------------------------------------------

    /** 命中标记。 */
    public static void hitMarker(org.bukkit.entity.LivingEntity entity, Particle particle) {
        if (entity == null || particle == null) return;
        spawn(entity.getLocation().add(0, entity.getHeight() / 2.0, 0), particle, 15, 0.2, 0.2, 0.2, 0.05);
    }

    /** 施法圈。 */
    public static void castCircle(org.bukkit.entity.LivingEntity entity, Particle particle, double radius) {
        if (entity == null) return;
        circle(entity.getLocation(), radius, particle, 24);
    }

    /** 拖尾（移动时逐 tick 调用）。 */
    public static void trail(Location location, Particle particle, int count, double spread) {
        spawn(location, particle, count, spread, spread, spread, 0.01);
    }

    /** 爆炸环 + 闪光。 */
    public static void explosion(Location center, double radius, Particle ringParticle, Particle flashParticle) {
        if (center == null) return;
        circle(center, radius, ringParticle, 40);
        spawn(center, flashParticle, 30, 0.5, 0.5, 0.5, 0.15);
    }

    /** 对一组位置执行自定义绘制。 */
    public static void forEach(Collection<Location> locations, Consumer<Location> action) {
        if (locations == null || action == null) return;
        for (Location loc : locations) {
            action.accept(loc);
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static Particle fallback() {
        try {
            return Particle.valueOf(EffectSettings.fallbackParticle().toUpperCase());
        } catch (Exception ex) {
            return Particle.ENCHANT;
        }
    }

    private static boolean safe(Location location) {
        return location != null
                && !Double.isNaN(location.getX()) && !Double.isNaN(location.getY()) && !Double.isNaN(location.getZ())
                && !Double.isInfinite(location.getX()) && !Double.isInfinite(location.getY())
                && !Double.isInfinite(location.getZ());
    }

    private static Vector randomUnitVector() {
        double theta = Math.random() * Math.PI * 2;
        double z = 2 * Math.random() - 1;
        double r = Math.sqrt(Math.max(0, 1 - z * z));
        return new Vector(r * Math.cos(theta), r * Math.sin(theta), z);
    }

    private static Vector perpendicular(Vector axis) {
        Vector a = axis.clone().normalize();
        Vector candidate = Math.abs(a.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector u = a.clone().crossProduct(candidate);
        if (u.lengthSquared() < 1.0e-6) {
            u = a.clone().crossProduct(new Vector(1, 0, 0));
        }
        return u.normalize();
    }
}
