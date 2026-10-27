package com.skillcore.utils;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 实体工具 — 有效性判断、距离、朝向、治疗/击杀等常用操作。
 * <p>
 * 所有跨世界调用都会返回安全默认值，避免 Bukkit 的
 * {@code IllegalStateException: Cannot measure distance between worlds}。
 */
public final class EntityUtils {

    private EntityUtils() {
    }

    public static boolean isValid(LivingEntity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    public static boolean isPlayer(LivingEntity entity) {
        return entity instanceof Player;
    }

    public static boolean isNotPlayer(LivingEntity entity) {
        return entity != null && !(entity instanceof Player);
    }

    public static Player asPlayer(LivingEntity entity) {
        return entity instanceof Player player ? player : null;
    }

    /** 玩家是否处于可正常战斗的状态（在线且非观察者）。 */
    public static boolean isPlayable(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        return player.getGameMode() != GameMode.SPECTATOR;
    }

    /** 同一世界内返回 true；任一方世界为 null 时返回 false。 */
    private static boolean sameWorld(Entity a, Entity b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    /** 两个实体的水平距离（跨世界返回 {@link Double#MAX_VALUE}）。 */
    public static double horizontalDistance(Entity a, Entity b) {
        if (a == null || b == null || !sameWorld(a, b)) {
            return Double.MAX_VALUE;
        }
        Location la = a.getLocation();
        Location lb = b.getLocation();
        double dx = la.getX() - lb.getX();
        double dz = la.getZ() - lb.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 两个实体的 3D 距离（跨世界返回 {@link Double#MAX_VALUE}）。 */
    public static double distance(Entity a, Entity b) {
        if (a == null || b == null || !sameWorld(a, b)) {
            return Double.MAX_VALUE;
        }
        return a.getLocation().distance(b.getLocation());
    }

    /** 实体是否在某个位置的范围内（跨世界返回 false）。 */
    public static boolean isInRange(Entity entity, Location center, double range) {
        if (entity == null || center == null || center.getWorld() == null
                || !center.getWorld().equals(entity.getWorld())
                || !Double.isFinite(range) || range < 0) {
            return false;
        }
        return entity.getLocation().distanceSquared(center) <= range * range;
    }

    /** 从 from 指向 to 的向量（跨世界返回零向量）。 */
    public static Vector direction(Entity from, Entity to) {
        if (from == null || to == null || !sameWorld(from, to)) {
            return new Vector();
        }
        return to.getLocation().toVector().subtract(from.getLocation().toVector());
    }

    /** 从位置指向实体的向量（跨世界返回零向量）。 */
    public static Vector direction(Location from, Entity to) {
        if (from == null || to == null || from.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return new Vector();
        }
        return to.getLocation().toVector().subtract(from.toVector());
    }

    /** 实体的水平朝向（单位向量；朝上/朝下时回退为 +Z）。 */
    public static Vector flatLook(Entity entity) {
        if (entity == null) {
            return new Vector();
        }
        Vector dir = entity.getLocation().getDirection().clone();
        dir.setY(0);
        return dir.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : dir.normalize();
    }

    /** 回满血（最大生命 <= 0 时不操作，避免 setHealth(0) 直接击杀）。 */
    public static void fullHeal(LivingEntity entity) {
        if (!isValid(entity)) {
            return;
        }
        double max = DamageUtils.getMaxHealth(entity);
        if (!Double.isFinite(max) || max <= 0) {
            return;
        }
        entity.setHealth(max);
    }

    /** 安全击杀。 */
    public static void kill(LivingEntity entity) {
        if (isValid(entity)) {
            entity.setHealth(0);
        }
    }

    /**
     * 目标是否是施法者的敌人。
     * 统一走 {@link TargetFilter}（过滤友军/宠物/NPC/盔甲架/同队/PvP 禁用世界）。
     */
    public static boolean isEnemy(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null || caster.equals(target)) {
            return false;
        }
        return !TargetFilter.shouldIgnoreTarget(caster, target);
    }

    /** 冻结实体速度。 */
    public static void freeze(Entity entity) {
        if (entity != null) {
            entity.setVelocity(new Vector(0, 0, 0));
        }
    }

    public static boolean isOnGround(LivingEntity entity) {
        return entity != null && entity.isOnGround();
    }

    /** 实体身体中心位置（脚底 + 半身高）。 */
    public static Location bodyCenter(LivingEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
    }

    /** 适合放进消息里的显示名（玩家用名字，其他用实体类型）。 */
    public static String displayName(LivingEntity entity) {
        if (entity == null) {
            return "Unknown";
        }
        return entity instanceof Player player ? player.getName() : entity.getType().name();
    }
}
