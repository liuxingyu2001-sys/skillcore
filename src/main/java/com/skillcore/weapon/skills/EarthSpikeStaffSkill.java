package com.skillcore.weapon.skills;

import com.skillcore.SkillCorePlugin;
import com.skillcore.effect.EffectSettings;
import com.skillcore.utils.AreaUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.SoundUtils;
import com.skillcore.utils.TargetFilter;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地刺法杖 —— 右键让大地隆起一排地刺穿刺敌人，被动吸血。
 *
 * <p>右键「地刺突涌」：以准星落点为目标，从脚下前方沿水平方向均匀铺开
 * {@code hit-count} 根地刺；地刺按 {@code hit-interval-ticks} 依次破土，
 * 每根破土瞬间对 {@code aoe-radius} 内敌人造成一次伤害（同一轮施法同一目标只吃一次），
 * 并向上击飞 {@code spike-uppercut-velocity}、按 {@code knockback} 推开、
 * 按 {@code slow-duration-ticks / slow-amplifier} 减速。</p>
 *
 * <p>每根伤害 = 武器伤害 × {@code aoe-damage-ratio}，走 {@code ctx.damageCustom}
 * （自动吃暴击/穿透，并自动按 {@code lifesteal} 吸血 —— 这就是被动吸血）。
 * 视觉是 {@code BlockDisplay} 的尖滴石方块：从地下插值升起、停留、再缩回，全部可配置。</p>
 *
 * <p>无目标技能：鼠标没指着实体也能放（落在准星指向的地面）。</p>
 */
@WeaponSkillInfo(id = "EARTH_SPIKE", aliases = {"EARTHSPIKE", "EARTH_STAFF"},
        description = "地刺法杖：右键召唤地刺穿刺敌人（击飞 + 减速）")
public final class EarthSpikeStaffSkill extends AbstractWeaponSkill {

    /** 单次施法最多地刺根数（配置写错也不至于刷屏） */
    private static final int MAX_SPIKES = 20;
    /** 单次施法最大铺开距离（格） */
    private static final double MAX_CAST_RANGE = 24.0;
    /** 单根地刺最大伤害半径（格） */
    private static final double MAX_SPIKE_RADIUS = 8.0;
    /** 地刺展示实体统一标记（异常残留时可 /kill @e[tag=...] 清理） */
    private static final String DISPLAY_TAG = "skillcore_earth_spike";

    /** 正在推进的地刺施法：玩家 UUID → 状态（退出 / 重载时取消并回收展示实体） */
    private final Map<UUID, SpikeCast> casts = new ConcurrentHashMap<>();

    public EarthSpikeStaffSkill() {
        super("EARTH_SPIKE");
    }

    @Override
    public boolean canUse(WeaponContext ctx) {
        return ctx != null && ctx.player() != null && ctx.player().isOnline()
                && !ctx.player().isDead()
                // 上一轮地刺还在破土时忽略这次点击（不生成新地刺，也不会额外扣冷却）
                && !casts.containsKey(ctx.player().getUniqueId());
    }

    // ------------------------------------------------------------------
    // 被动：吸血（普攻）
    // ------------------------------------------------------------------

    /**
     * 普攻命中时按 {@code lifesteal} 回血。
     * <p>
     * 技能（地刺）伤害不经过这里：{@code ctx.damageCustom} 内部已经按同一比例吸过一次血，
     * 这里再吸就会双倍回血，所以只处理普通近战命中。
     */
    @Override
    public void onHit(WeaponContext ctx, LivingEntity victim, double damage) {
        if (ctx == null || victim == null || !(damage > 0)) return;
        ctx.healSelfByDamage(damage);
    }

    // ------------------------------------------------------------------
    // 右键：地刺突涌
    // ------------------------------------------------------------------

    @Override
    public void onRightClick(WeaponContext ctx) {
        Player player = ctx.player();
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (player == null || plugin == null) return;
        World world = player.getWorld();
        if (world == null) return;

        var stats = ctx.stats();
        Location origin = player.getLocation().clone();
        Location aim = ctx.aimLocation();
        if (aim == null || aim.getWorld() == null || !aim.getWorld().equals(world)) {
            aim = origin.clone().add(player.getEyeLocation().getDirection().multiply(stats.range()));
        }

        // 水平方向：玩家 → 准星落点（抬眼/低头也按水平投影铺在地面上）
        Vector flat = aim.toVector().subtract(origin.toVector());
        flat.setY(0);
        if (flat.lengthSquared() < 1.0e-4) {
            flat = player.getLocation().getDirection();
            flat.setY(0);
        }
        if (flat.lengthSquared() < 1.0e-4) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();

        int count = Math.max(1, Math.min(stats.hitCount(), MAX_SPIKES));
        double range = Math.max(2.0, Math.min(stats.range(), MAX_CAST_RANGE));
        double horizontal = Math.hypot(aim.getX() - origin.getX(), aim.getZ() - origin.getZ());
        double distance = Math.min(range, Math.max(1.0, horizontal));
        double spacing = distance / count;
        double radius = Math.max(0.5, Math.min(stats.aoeRadius(), MAX_SPIKE_RADIUS));
        // 范围伤害统一吃 aoe-damage-ratio；未配置（<=0）时按 100%
        double ratio = stats.aoeDamageRatio() > 0 ? stats.aoeDamageRatio() : 1.0;
        double perSpike = stats.damage() * ratio;
        int stagger = (int) Math.max(0, Math.min(stats.hitIntervalTicks(), 20.0));

        SpikeCast cast = new SpikeCast(ctx, player);
        cast.perSpikeDamage = perSpike;
        cast.radius = radius;
        cast.knockback = stats.knockbackStrength();
        cast.uppercut = stats.spikeUppercutVelocity();
        cast.slowTicks = stats.slowDurationTicks();
        cast.slowAmplifier = stats.slowAmplifier();
        cast.riseTicks = Math.max(1, stats.spikeRiseTicks());
        cast.holdTicks = Math.max(0, stats.spikeHoldTicks());
        cast.fallTicks = Math.max(1, stats.spikeFallTicks());
        cast.scale = Math.max(0.2f, stats.spikeScale());
        cast.particle = parseParticle(stats.particle());
        cast.sound = parseSound(stats.sound());
        cast.volume = stats.soundVolume();
        cast.pitch = stats.soundPitch();
        cast.blockData = spikeBlockData();

        // 先把每根地刺的落点与时间轴算好（找不到地面的那根直接跳过）
        double originY = origin.getY();
        double aimY = aim.getY();
        for (int i = 0; i < count; i++) {
            double along = spacing * (i + 1);
            // 高度按「脚下 → 准星」线性插值：站高处往低处放时地刺会顺着坡面铺下去
            double t = (i + 1.0) / count;
            Location probe = new Location(world,
                    origin.getX() + flat.getX() * along,
                    originY + (aimY - originY) * t,
                    origin.getZ() + flat.getZ() * along);
            Location ground = findGround(probe);
            if (ground == null) {
                // 准星落在空中（打飞行目标/抬头）：退回玩家脚下高度再找一次地面，
                // 保证地刺仍然铺在地面上，而不是整轮施法直接空放。
                probe.setY(originY);
                ground = findGround(probe);
            }
            if (ground == null) continue;

            Spike spike = new Spike(ground);
            spike.spawnTick = i * stagger;
            spike.damageTick = spike.spawnTick + cast.riseTicks;
            spike.retractTick = spike.damageTick + cast.holdTicks;
            spike.removeTick = spike.retractTick + cast.fallTicks;
            cast.spikes.add(spike);
        }
        if (cast.spikes.isEmpty()) return;

        casts.put(player.getUniqueId(), cast);

        // 起手：杖尖聚土 + 闷响（音色取技能配置的 sound）
        if (EffectSettings.effectsEnabled()) {
            ParticleUtils.spawn(origin.clone().add(0, 0.2, 0), Particle.CRIT, 12, 0.35, 0.1, 0.35, 0.02);
            SoundUtils.play(origin, cast.sound, cast.volume, clampPitch(cast.pitch * 0.8f));
        }

        cast.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (tick(cast)) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 推进一帧；返回 true 表示整轮施法结束。 */
    private boolean tick(SpikeCast cast) {
        cast.tick++;
        Player player = cast.player;
        if (player == null || !player.isOnline() || player.isDead()) {
            finish(cast, true);
            return true;
        }

        boolean allDone = true;
        for (Spike spike : cast.spikes) {
            if (spike.removed) continue;
            allDone = false;

            if (!spike.spawned && cast.tick >= spike.spawnTick) {
                spike.spawned = true;
                spawnVisual(cast, spike);
            }
            if (!spike.damaged && cast.tick >= spike.damageTick) {
                spike.damaged = true;
                impactSpike(cast, spike);
            }
            if (!spike.retracting && cast.tick >= spike.retractTick) {
                spike.retracting = true;
                retractVisual(cast, spike);
            }
            if (cast.tick >= spike.removeTick) {
                removeVisual(spike);
                spike.removed = true;
            }
        }

        if (allDone) {
            finish(cast, false);
            return true;
        }
        return false;
    }

    /** 结束施法：取消任务、按需回收展示实体、移除状态。 */
    private void finish(SpikeCast cast, boolean removeDisplays) {
        if (cast == null) return;
        if (cast.task != null) {
            cast.task.cancel();
            cast.task = null;
        }
        if (removeDisplays) {
            for (Spike spike : cast.spikes) {
                removeVisual(spike);
                spike.removed = true;
            }
        }
        if (cast.player != null) {
            casts.remove(cast.player.getUniqueId(), cast);
        }
    }

    // ------------------------------------------------------------------
    // 地刺表现：方块展示实体（破土 → 停留 → 缩回）
    // ------------------------------------------------------------------

    /** 生成一根埋在地下的地刺，下一 tick 用客户端插值升到地面。 */
    private void spawnVisual(SpikeCast cast, Spike spike) {
        if (!EffectSettings.effectsEnabled()) return;
        Location ground = spike.ground;
        World world = ground.getWorld();
        if (world == null) return;

        final float scale = cast.scale;
        BlockDisplay display = world.spawn(ground, BlockDisplay.class, d -> {
            d.setBlock(cast.blockData);
            d.setPersistent(false);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            d.addScoreboardTag(DISPLAY_TAG);
            d.setTeleportDuration(2);
            d.setInterpolationDuration(0);
            d.setInterpolationDelay(0);
            // 初始埋在地下（不可见），下一 tick 插值升起
            d.setTransformation(spikeTransform(scale, -scale * 1.4f));
        });
        spike.display = display;

        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (display.isValid()) {
                    display.setInterpolationDuration(cast.riseTicks);
                    display.setTransformation(spikeTransform(scale, 0f));
                }
            });
        }

        // 破土碎屑 + 地刺音效
        ParticleUtils.blockDust(ground.clone().add(0, 0.15, 0), groundBlockData(ground), 14);
        SoundUtils.play(ground, cast.sound, cast.volume * 0.8f, clampPitch(cast.pitch * 0.75f));
    }

    /** 停留结束：把地刺缩回地下（保留实体直到 removeTick）。 */
    private void retractVisual(SpikeCast cast, Spike spike) {
        BlockDisplay display = spike.display;
        if (display == null || !display.isValid()) return;
        display.setInterpolationDuration(cast.fallTicks);
        display.setTransformation(spikeTransform(cast.scale, -cast.scale * 1.4f));
    }

    private void removeVisual(Spike spike) {
        if (spike != null && spike.display != null && spike.display.isValid()) {
            spike.display.remove();
        }
        if (spike != null) {
            spike.display = null;
        }
    }

    private static Transformation spikeTransform(float scale, float yOffset) {
        return new Transformation(
                new Vector3f(0f, yOffset, 0f),
                new Quaternionf(0f, 0f, 0f, 1f),
                new Vector3f(scale, scale, scale),
                new Quaternionf(0f, 0f, 0f, 1f));
    }

    /** 地刺方块：尖滴石（MIDDLE 段 + 朝上），放大后就是一根从地里冒出的石刺。 */
    private static BlockData spikeBlockData() {
        BlockData data = Material.POINTED_DRIPSTONE.createBlockData();
        if (data instanceof org.bukkit.block.data.type.PointedDripstone dripstone) {
            dripstone.setThickness(org.bukkit.block.data.type.PointedDripstone.Thickness.MIDDLE);
            dripstone.setVerticalDirection(BlockFace.UP);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 命中结算
    // ------------------------------------------------------------------

    /** 一根地刺破土瞬间的范围伤害（同一轮施法同一目标只结算一次）。 */
    private void impactSpike(SpikeCast cast, Spike spike) {
        Player player = cast.player;
        Location ground = spike.ground;
        World world = ground.getWorld();
        if (player == null || world == null) return;

        List<LivingEntity> targets = TargetFilter.filter(player,
                AreaUtils.getSphere(ground, cast.radius, e -> TargetFilter.isAttackable(player, e)));

        for (LivingEntity target : targets) {
            if (target == null || target.isDead() || !target.isValid()) continue;
            // 同一轮施法只吃一根地刺的伤害，避免 20 根叠满
            if (!cast.hitTargets.add(target.getUniqueId())) continue;

            cast.ctx.damageCustom(target, cast.perSpikeDamage);

            // 从地刺位置向外推开 + 向上顶起
            if (cast.knockback > 0) {
                DisplacementUtils.knockback(target, ground, cast.knockback, 0.0);
            }
            if (cast.uppercut != 0) {
                Vector velocity = target.getVelocity();
                target.setVelocity(velocity.setY(Math.max(velocity.getY(), cast.uppercut)));
            }
            target.setFallDistance(0);

            if (cast.slowTicks > 0) {
                PotionUtils.slowness(target, cast.slowTicks, cast.slowAmplifier);
            }

            if (EffectSettings.effectsEnabled()) {
                Location at = target.getLocation().add(0, target.getHeight() / 2.0, 0);
                ParticleUtils.spawn(at, cast.particle, 8, 0.2, 0.3, 0.2, 0.1);
                ParticleUtils.blockDust(at, cast.blockData, 6);
            }
        }

        if (EffectSettings.effectsEnabled()) {
            ParticleUtils.blockDust(ground.clone().add(0, 0.2, 0), groundBlockData(ground), 10);
            SoundUtils.play(ground, cast.sound, cast.volume, clampPitch(cast.pitch * 1.1f));
        }
    }

    // ------------------------------------------------------------------
    // 落点 / 方块数据
    // ------------------------------------------------------------------

    /** 在期望位置附近向下找一处「下方实心、脚部非实心」的地面；找不到返回 null。 */
    private static Location findGround(Location probe) {
        if (probe == null) return null;
        World world = probe.getWorld();
        if (world == null) return null;
        int x = probe.getBlockX();
        int z = probe.getBlockZ();
        // 未加载区块的 getBlockAt 会同步加载区块（主线程卡顿），直接跳过这一根
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;

        int startY = Math.min(world.getMaxHeight() - 2, probe.getBlockY() + 1);
        int endY = Math.max(world.getMinHeight() + 1, probe.getBlockY() - 4);
        for (int y = startY; y >= endY; y--) {
            Block below = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            if (below.getType().isSolid() && !feet.getType().isSolid()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** 地面方块数据（区块未加载 / 读取失败时退回地刺材质）。 */
    private static BlockData groundBlockData(Location ground) {
        World world = ground.getWorld();
        if (world != null && world.isChunkLoaded(ground.getBlockX() >> 4, ground.getBlockZ() >> 4)) {
            try {
                return ground.getBlock().getBlockData();
            } catch (RuntimeException ignored) {
                // 区块刚好卸载：退回地刺材质，不影响伤害
            }
        }
        return Material.POINTED_DRIPSTONE.createBlockData();
    }

    private static Particle parseParticle(String name) {
        if (name == null || name.isBlank()) return Particle.CRIT;
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Particle.CRIT;
        }
    }

    private static Sound parseSound(String name) {
        if (name == null || name.isBlank()) return Sound.ITEM_TRIDENT_HIT_GROUND;
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Sound.ITEM_TRIDENT_HIT_GROUND;
        }
    }

    /** 音调保持在 Bukkit 可用范围内（0.5~2.0）。 */
    private static float clampPitch(float pitch) {
        if (!Float.isFinite(pitch)) return 1.0f;
        return Math.max(0.5f, Math.min(pitch, 2.0f));
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void cleanup() {
        for (SpikeCast cast : casts.values()) {
            finish(cast, true);
        }
        casts.clear();
    }

    @Override
    public void onQuit(Player player) {
        if (player == null) return;
        SpikeCast cast = casts.get(player.getUniqueId());
        if (cast != null) {
            finish(cast, true);
        }
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /** 单根地刺的时间轴与展示实体。 */
    private static final class Spike {
        final Location ground;
        BlockDisplay display;
        int spawnTick;
        int damageTick;
        int retractTick;
        int removeTick;
        boolean spawned;
        boolean damaged;
        boolean retracting;
        boolean removed;

        Spike(Location ground) {
            this.ground = ground;
        }
    }

    /** 一轮地刺施法的共享参数。 */
    private static final class SpikeCast {
        final WeaponContext ctx;
        final Player player;
        final List<Spike> spikes = new ArrayList<>();
        final Set<UUID> hitTargets = new HashSet<>();
        BukkitTask task;
        int tick;
        double perSpikeDamage;
        double radius;
        double knockback;
        double uppercut;
        int slowTicks;
        int slowAmplifier;
        int riseTicks;
        int holdTicks;
        int fallTicks;
        float scale;
        float volume;
        float pitch;
        Particle particle;
        Sound sound;
        BlockData blockData;

        SpikeCast(WeaponContext ctx, Player player) {
            this.ctx = ctx;
            this.player = player;
        }
    }
}
