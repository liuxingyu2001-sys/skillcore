package com.skillcore.effect;

import com.skillcore.hook.PacketEventsHook;
import com.skillcore.utils.ParticleUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 特效工厂 — 用一行代码构建内置特效，并支持组合。
 * <pre>
 * SkillEffect cast = Effects.ring(Particle.END_ROD, 2.0, 24)
 *         .andThen(Effects.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f));
 * cast.play(player, player.getLocation());
 *
 * // CraftEngine 模型显示物
 * Effects.model("my_pack:sword_aura", 1.5f, 40).play(player.getLocation());
 * </pre>
 */
public final class Effects {

    private Effects() {
    }

    // ------------------------------------------------------------------
    // 基础
    // ------------------------------------------------------------------

    /** 空特效（占位）。 */
    public static SkillEffect none() {
        return context -> {
        };
    }

    /** 点/爆发粒子。 */
    public static SkillEffect particle(Particle particle, int count) {
        return particle(particle, count, 0.0, 0.0, 0.0, 0.0);
    }

    /** 带扩散的粒子爆发。 */
    public static SkillEffect particle(Particle particle, int count, double spread) {
        return particle(particle, count, spread, spread, spread, 0.0);
    }

    /** 完整参数粒子。 */
    public static SkillEffect particle(Particle particle, int count,
                                       double offsetX, double offsetY, double offsetZ, double extra) {
        return new ParticleEffect(particle, count, offsetX, offsetY, offsetZ, extra);
    }

    // ------------------------------------------------------------------
    // 形状
    // ------------------------------------------------------------------

    /** XZ 平面圆环。 */
    public static SkillEffect ring(Particle particle, double radius, int points) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.ring(context.location(), radius, particle, points, new Vector(0, 1, 0));
            }
        };
    }

    /** 面向施法方向的竖直圆环。 */
    public static SkillEffect ringFacing(Particle particle, double radius, int points) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.ring(context.location(), radius, particle, points, context.direction());
            }
        };
    }

    /** 球壳。 */
    public static SkillEffect sphere(Particle particle, double radius, int points) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.sphereShell(context.location(), radius, particle, points);
            }
        };
    }

    /** 螺旋。 */
    public static SkillEffect helix(Particle particle, double radius, double height, int points) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.helix(context.location(), radius, height, particle, points);
            }
        };
    }

    /** 沿朝向的锥形。 */
    public static SkillEffect cone(Particle particle, double angleDeg, double length, int points) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.cone(context.location(), context.direction(), angleDeg, length, particle, points);
            }
        };
    }

    /** 沿朝向的光束。 */
    public static SkillEffect beam(Particle particle, double length, double step) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.beam(context.location(), context.direction(), length, step, particle);
            }
        };
    }

    /** 从施法位置到目标位置的连线。 */
    public static SkillEffect lineToTarget(Particle particle, double step) {
        return context -> {
            if (context.valid() && context.target() != null) {
                ParticleUtils.line(context.location(), context.target().getLocation(), particle, step);
            }
        };
    }

    /** 爆炸环 + 闪光。 */
    public static SkillEffect explosion(Particle ringParticle, Particle flashParticle, double radius) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.explosion(context.location(), radius, ringParticle, flashParticle);
            }
        };
    }

    // ------------------------------------------------------------------
    // 彩色尘
    // ------------------------------------------------------------------

    /** 在当前位置生成彩色尘。 */
    public static SkillEffect dust(Color color, float size, int count) {
        return context -> {
            if (context.valid()) {
                ParticleUtils.dust(context.location(), color, size, count);
            }
        };
    }

    /** 在当前位置以球形扩散生成彩色尘。 */
    public static SkillEffect dust(Color color, float size, int count, double spread) {
        return context -> {
            if (!context.valid()) return;
            for (int i = 0; i < count; i++) {
                Location p = context.location().clone().add(
                        (Math.random() * 2 - 1) * spread,
                        (Math.random() * 2 - 1) * spread,
                        (Math.random() * 2 - 1) * spread);
                ParticleUtils.dust(p, color, size, 1);
            }
        };
    }

    // ------------------------------------------------------------------
    // 闪电
    // ------------------------------------------------------------------

    /** 沿朝向方向劈出一段闪电。 */
    public static SkillEffect bolt(Particle particle, double length, double jaggedness, int segments) {
        return context -> {
            if (!context.valid()) return;
            Location to = context.location().clone()
                    .add(context.direction().clone().multiply(length));
            ParticleUtils.bolt(context.location(), to, particle, jaggedness, segments);
        };
    }

    /** 在固定两点间生成闪电。 */
    public static SkillEffect boltBetween(Location from, Location to, Particle particle,
                                          double jaggedness, int segments) {
        return context -> ParticleUtils.bolt(from, to, particle, jaggedness, segments);
    }

    /** 从天空向脚下劈下多道随机闪电。 */
    public static SkillEffect skyStrikes(double height, int strikes, double spreadRadius,
                                         Particle particle, double jaggedness, int segments) {
        return context -> {
            if (!context.valid()) return;
            for (int i = 0; i < strikes; i++) {
                Location ground = context.location().clone().add(
                        (Math.random() * 2 - 1) * spreadRadius, 0,
                        (Math.random() * 2 - 1) * spreadRadius);
                Location sky = ground.clone().add(0, height, 0);
                ParticleUtils.bolt(sky, ground, particle, jaggedness, segments);
            }
        };
    }

    /** 从天空劈下彩色尘闪电。 */
    public static SkillEffect skyStrikesDust(double height, int strikes, double spreadRadius,
                                             Color color, float size, int dustCount,
                                             double jaggedness, int segments) {
        return context -> {
            if (!context.valid()) return;
            for (int i = 0; i < strikes; i++) {
                Location ground = context.location().clone().add(
                        (Math.random() * 2 - 1) * spreadRadius, 0,
                        (Math.random() * 2 - 1) * spreadRadius);
                Location sky = ground.clone().add(0, height, 0);
                ParticleUtils.boltDust(sky, ground, color, size, dustCount, jaggedness, segments);
            }
        };
    }

    // ------------------------------------------------------------------
    // 动画形状
    // ------------------------------------------------------------------

    /** 随时间扩散的圆环（从 fromRadius 到 toRadius，共 durationTicks 个环）。 */
    public static SkillEffect expandingRing(Particle particle, double fromRadius, double toRadius,
                                            int points, long durationTicks) {
        return context -> {
            if (!context.valid()) return;
            long steps = Math.max(1, durationTicks);
            for (int i = 0; i <= steps; i++) {
                double r = fromRadius + (toRadius - fromRadius) * (i / (double) steps);
                final double radius = r;
                SkillEffect ring = ctx -> ParticleUtils.ring(ctx.location(), radius, particle, points, new Vector(0, 1, 0));
                Effects.delayed(ring, i).play(context);
            }
        };
    }

    /** 地面裂纹：从中心向外放射的闪电状折线。 */
    public static SkillEffect groundCracks(double radius, int cracks, Particle particle, double jaggedness) {
        return context -> {
            if (!context.valid()) return;
            for (int i = 0; i < cracks; i++) {
                double angle = 2 * Math.PI * i / Math.max(1, cracks);
                Location start = context.location().clone().add(0, 0.12, 0);
                Location end = start.clone().add(
                        Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                ParticleUtils.bolt(start, end, particle, jaggedness, 6);
            }
        };
    }

    /** 环绕上升的粒子（围绕当前位置做 durationTicks 圈的螺旋）。 */
    public static SkillEffect orbit(Particle particle, double radius, double height,
                                    double turns, int points, long durationTicks) {
        return context -> {
            if (!context.valid()) return;
            long steps = Math.max(1, durationTicks);
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double angle = t * turns * 2 * Math.PI;
                Location p = context.location().clone().add(
                        Math.cos(angle) * radius, height * t, Math.sin(angle) * radius);
                final Location point = p;
                SkillEffect dot = ctx -> ParticleUtils.spawn(point, particle, 1);
                Effects.delayed(dot, i).play(context);
            }
        };
    }

    /**
     * 地面碎裂：方块碎屑粒子 +（PacketEvents 软依赖时）方块裂纹动画后恢复。
     * <p>
     * 只影响客户端表现，服务器方块不变。
     */
    public static SkillEffect groundShatter(double radius) {
        return context -> {
            if (!context.valid()) return;
            Location center = context.location();
            World world = context.world();
            int r = (int) Math.ceil(Math.max(0, radius));
            List<Block> blocks = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 1) continue;
                    Block b = world.getHighestBlockAt(center.getBlockX() + dx, center.getBlockZ() + dz);
                    int guard = 0;
                    while (b != null && !b.getType().isSolid() && guard++ < 5
                            && b.getY() > world.getMinHeight()) {
                        b = b.getRelative(BlockFace.DOWN);
                    }
                    if (b != null && b.getType().isSolid() && !b.getType().isAir()) {
                        blocks.add(b);
                    }
                }
            }
            if (blocks.isEmpty()) return;

            // 方块碎屑飞溅（始终可用，不依赖 PacketEvents）
            for (Block b : blocks) {
                ParticleUtils.blockDust(b.getLocation().add(0.5, 0.6, 0.5), b.getBlockData(), 10);
            }

            // 数据包裂纹动画（PacketEvents 软依赖）
            List<Player> viewers = new ArrayList<>(center.getNearbyPlayers(radius + 16));
            PacketEventsHook.crackAndRestore(viewers, blocks, 6);
        };
    }

    // ------------------------------------------------------------------
    // 音效
    // ------------------------------------------------------------------

    public static SkillEffect sound(Sound sound, float volume, float pitch) {
        return new SoundEffect(sound, volume, pitch, 0);
    }

    public static SkillEffect sound(Sound sound) {
        return new SoundEffect(sound, 1.0f, 1.0f, 0);
    }

    /** 半径内可听的音效。 */
    public static SkillEffect soundRadius(Sound sound, float volume, float pitch, double radius) {
        return new SoundEffect(sound, volume, pitch, radius);
    }

    // ------------------------------------------------------------------
    // 显示物 / CraftEngine 模型
    // ------------------------------------------------------------------

    /** 用物品作为显示物（transient ItemDisplay）。 */
    public static SkillEffect display(ItemStack item, float scale, long removeAfterTicks) {
        return new DisplayEffect(item, null, scale, removeAfterTicks, false);
    }

    /** 用 CraftEngine 模型作为显示物。 */
    public static SkillEffect model(String craftEngineModel, float scale, long removeAfterTicks) {
        return new DisplayEffect(null, craftEngineModel, scale, removeAfterTicks, false);
    }

    /** 用 CraftEngine 模型作为显示物，并使其朝向施法方向。 */
    public static SkillEffect modelFacing(String craftEngineModel, float scale, long removeAfterTicks) {
        return new DisplayEffect(null, craftEngineModel, scale, removeAfterTicks, true);
    }

    // ------------------------------------------------------------------
    // 组合
    // ------------------------------------------------------------------

    /** 多个特效同时播放。 */
    public static SkillEffect parallel(SkillEffect... effects) {
        return new CompositeEffect(effects);
    }

    /** 延迟播放。 */
    public static SkillEffect delayed(SkillEffect effect, long ticks) {
        return new DelayedEffect(effect, ticks);
    }

    /** 按固定间隔依次播放（第 i 个延迟 i*intervalTicks）。 */
    public static SkillEffect sequence(long intervalTicks, SkillEffect... effects) {
        CompositeEffect composite = new CompositeEffect();
        if (effects != null) {
            for (int i = 0; i < effects.length; i++) {
                if (effects[i] == null) continue;
                composite.add(intervalTicks <= 0 ? effects[i] : new DelayedEffect(effects[i], intervalTicks * i));
            }
        }
        return composite;
    }

    /** 在多个位置各播放一次同一特效。 */
    public static SkillEffect at(SkillEffect effect, Location... locations) {
        return context -> {
            if (effect == null || locations == null) return;
            for (Location loc : locations) {
                if (loc != null) {
                    effect.play(loc);
                }
            }
        };
    }
}
