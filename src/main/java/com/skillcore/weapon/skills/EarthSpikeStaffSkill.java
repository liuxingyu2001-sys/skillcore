package com.skillcore.weapon.skills;

import com.skillcore.SkillCorePlugin;
import com.skillcore.effect.EffectSettings;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.MessageUtils;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地刺法杖 —— BLAZE_ROD 法杖，两个技能都会<b>锁定目标并造成伤害</b>。
 *
 * <ul>
 *   <li><b>右键「地刺喷射」</b>：优先锁定准星直指 / 锥形内最佳目标，射出一根会追踪锁定目标的地刺；
 *       命中处炸开刺环，每根地刺破土时对范围内敌人造成一次伤害并掀飞。</li>
 *   <li><b>Shift+右键「雷霆召唤」</b>：可同时锁定多个目标（主目标优先、其余按距离），
 *       逐个目标头顶牵引落雷（纯视觉雷击，不点火），雷光标记并造成范围伤害；
 *       同时给施法者注入「雷力」，下一发地刺附带雷弧。</li>
 * </ul>
 *
 * <p>伤害统一走 {@link WeaponContext#damageCustom}（吃暴击 / 穿透 / 吸血），
 * 目标过滤统一走 {@code TargetFilter}；数值全部来自 {@code skills/earth_staff.yml}。
 */
@WeaponSkillInfo(id = "EARTH_SPIKE", aliases = {"EARTHSPIKE", "EARTH_STAFF"},
        description = "地刺法杖：右键锁敌发射地刺，Shift+右键锁敌召唤雷霆（不可破坏）")
public final class EarthSpikeStaffSkill extends AbstractWeaponSkill {

    /** 单次喷发最多地刺根数 */
    private static final int MAX_SPIKES = 20;
    /** 单次雷霆最多落雷道数 */
    private static final int MAX_STRIKES = 16;
    /** 单次雷霆最多同时锁定目标数 */
    private static final int MAX_LOCK_TARGETS = 8;
    /** 弹射物最长存活 tick（兜底，正常会先命中/到射程） */
    private static final int MAX_PROJECTILE_TICKS = 80;
    /** 弹射物 / 落雷的最大作用距离（格） */
    private static final double MAX_CAST_RANGE = 32.0;
    /** 掀飞 / 伤害的最大作用半径（格） */
    private static final double MAX_EFFECT_RADIUS = 8.0;
    /** 地刺展示实体统一标记（异常残留时可 /kill @e[tag=...] 清理） */
    private static final String DISPLAY_TAG = "skillcore_earth_spike";
    /** 动作栏前缀 */
    private static final String MSG_PREFIX = "&8[&2地刺法杖&8] &7";

    /** 正在飞的弹射物（按玩家 UUID） */
    private final Map<UUID, Projectile> projectiles = new ConcurrentHashMap<>();
    /** 正在推进的地刺喷发（按玩家 UUID） */
    private final Map<UUID, SpikeCast> casts = new ConcurrentHashMap<>();
    /** 正在推进的落雷序列（按玩家 UUID） */
    private final Map<UUID, LightningCast> lightningCasts = new ConcurrentHashMap<>();
    /** 雷力加持：player UUID -> 过期时间(ms)，下一发地刺附带雷光 */
    private final Map<UUID, Long> chargedUntil = new ConcurrentHashMap<>();

    public EarthSpikeStaffSkill() {
        super("EARTH_SPIKE");
    }

    @Override
    public boolean canUse(WeaponContext ctx) {
        if (ctx == null || ctx.player() == null || !ctx.player().isOnline() || ctx.player().isDead()) {
            return false;
        }
        // 弹射物还在飞时不能再射一发；Shift+右键（雷）不受限制
        return ctx.isSneaking() || !projectiles.containsKey(ctx.player().getUniqueId());
    }

    /**
     * 被动吸血：普攻命中按 {@code lifesteal} 回血。
     * <p>
     * 技能（地刺 / 雷霆）伤害不经过这里：{@code ctx.damageCustom} 内部已经按同一比例吸过一次血，
     * 这里再吸就会双倍回血，所以只处理普通近战命中。
     */
    @Override
    public void onHit(WeaponContext ctx, LivingEntity victim, double damage) {
        if (ctx == null || victim == null || !(damage > 0)) return;
        ctx.healSelfByDamage(damage);
    }

    // ------------------------------------------------------------------
    // 右键：地刺喷射（锁定目标）
    // ------------------------------------------------------------------

    @Override
    public void onRightClick(WeaponContext ctx) {
        Player player = ctx.player();
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (player == null || plugin == null) return;
        World world = player.getWorld();
        if (world == null) return;
        if (projectiles.containsKey(player.getUniqueId())) return;

        var stats = ctx.stats();
        // 锁定：准星直指 > 锥形辅助瞄准；没锁到就沿视线直射
        LivingEntity lock = lockTarget(ctx);
        Location eye = ctx.eyeLocation().clone();
        Vector direction;
        if (lock != null) {
            direction = lock.getLocation().add(0, lock.getHeight() * 0.5, 0)
                    .toVector().subtract(eye.toVector());
        } else {
            direction = eye.getDirection();
        }
        if (direction.lengthSquared() < 1.0e-8) {
            direction = new Vector(0, 0, 1);
        }
        direction = direction.normalize();

        Projectile proj = new Projectile(ctx, player, world, eye, direction);
        proj.target = lock;
        proj.speed = Math.max(0.1, Math.min(stats.spikeProjectileSpeed(), 10.0));
        proj.maxRange = Math.max(2.0, Math.min(stats.range(), MAX_CAST_RANGE));
        proj.launchRadius = Math.max(0.5, Math.min(stats.aoeRadius(), MAX_EFFECT_RADIUS));
        proj.damage = stats.damage() * Math.max(0.0, stats.aoeDamageRatio());
        proj.uppercut = stats.spikeUppercutVelocity();
        proj.push = stats.knockbackStrength();
        proj.particle = parseParticle(stats.particle());
        proj.sound = parseSound(stats.sound());
        proj.volume = stats.soundVolume();
        proj.pitch = stats.soundPitch();
        proj.blockData = spikeBlockData();
        proj.display = spawnProjectileDisplay(proj);

        projectiles.put(player.getUniqueId(), proj);
        MessageUtils.sendActionBar(player, MSG_PREFIX + "&b地刺喷射 &7· "
                + (lock != null ? "&c锁定 &f" + lock.getName() : "&f地刺飞射而出"));
        SoundUtils.play(eye, Sound.ITEM_TRIDENT_THROW, 0.9f, 1.2f);

        proj.task = new BukkitRunnable() {
            @Override
            public void run() {
                tickProjectile(proj);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 弹射物逐 tick 飞行：锁定时每 tick 修正方向，命中方块 / 目标 / 射程尽头即炸开。 */
    private void tickProjectile(Projectile proj) {
        Player player = proj.player;
        proj.ticks++;
        if (player == null || !player.isOnline() || player.isDead() || proj.ticks > MAX_PROJECTILE_TICKS) {
            impact(proj, proj.position);
            return;
        }

        // 锁定目标追踪：目标死亡 / 换世界 / 跑出射程则解除锁定，退回直线飞行
        LivingEntity target = proj.target;
        if (target != null && (!target.isValid() || target.isDead()
                || target.getWorld() != proj.world
                || target.getLocation().distanceSquared(proj.position) > proj.maxRange * proj.maxRange * 2.25)) {
            target = null;
            proj.target = null;
        }
        if (target != null) {
            Location aim = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            Vector toAim = aim.toVector().subtract(proj.position.toVector());
            double distance = toAim.length();
            if (distance > 1.0e-6) {
                proj.direction = toAim.multiply(1.0 / distance);
            }
            // 已贴脸：直接结算，避免高速弹射物从目标身上穿过去
            if (distance <= proj.speed + 0.8) {
                impact(proj, target.getLocation());
                return;
            }
        }

        Location next = proj.position.clone().add(proj.direction.clone().multiply(proj.speed));
        if (next.getWorld() == null) {
            impact(proj, proj.position);
            return;
        }
        proj.traveled += proj.speed;

        LivingEntity hitEntity = target == null
                ? findEntity(next, Math.max(0.4, proj.launchRadius * 0.5), player)
                : null;
        boolean hitBlock = next.getBlock().getType().isSolid();
        if (hitBlock || hitEntity != null || proj.traveled >= proj.maxRange) {
            impact(proj, hitEntity != null ? hitEntity.getLocation() : next);
            return;
        }

        proj.position = next;
        if (proj.display != null && proj.display.isValid()) {
            proj.display.teleport(next);
            proj.display.setTransformation(projectileTransform(proj.direction, 1.0f));
        }
        ParticleUtils.spawn(next, proj.particle, 3, 0.06, 0.06, 0.06, 0.0);
    }

    /** 弹射物落地：碎屑 / 音效 / 炸开刺环。 */
    private void impact(Projectile proj, Location point) {
        if (proj.task != null) {
            proj.task.cancel();
            proj.task = null;
        }
        if (proj.player != null) {
            projectiles.remove(proj.player.getUniqueId(), proj);
        }
        if (proj.display != null && proj.display.isValid()) {
            proj.display.remove();
        }
        proj.display = null;

        Location ground = findGround(point);
        if (ground == null) {
            ground = point.clone();
        }

        if (EffectSettings.effectsEnabled()) {
            ParticleUtils.blockDust(ground.clone().add(0, 0.2, 0), groundBlockData(ground), 16);
            ParticleUtils.spawn(ground.clone().add(0, 0.3, 0), proj.particle, 12, 0.3, 0.2, 0.3, 0.05);
            SoundUtils.play(ground, Sound.ITEM_TRIDENT_HIT_GROUND, 1.0f, 0.9f);
            SoundUtils.play(ground, proj.sound, proj.volume, clampPitch(proj.pitch));
        }

        erupt(proj, ground);
    }

    /** 在落点炸开刺环：每根地刺破土时对范围内敌人造成一次伤害并掀飞。 */
    private void erupt(Projectile proj, Location center) {
        var stats = proj.ctx.stats();
        int count = Math.max(1, Math.min(stats.hitCount(), MAX_SPIKES));
        int stagger = (int) Math.max(0, Math.min(stats.hitIntervalTicks(), 20.0));
        // 刺环半径取作用半径的 55%，让「伤害圈」看起来恰好被地刺围住
        double ringRadius = Math.max(0.6, proj.launchRadius * 0.55);

        SpikeCast cast = new SpikeCast(proj.ctx, proj.player);
        cast.launchRadius = proj.launchRadius;
        cast.damage = proj.damage;
        cast.uppercut = proj.uppercut;
        cast.push = proj.push;
        cast.riseTicks = Math.max(1, stats.spikeRiseTicks());
        cast.holdTicks = Math.max(0, stats.spikeHoldTicks());
        cast.fallTicks = Math.max(1, stats.spikeFallTicks());
        cast.scale = Math.max(0.2f, stats.spikeScale());
        cast.particle = proj.particle;
        cast.sound = proj.sound;
        cast.volume = proj.volume;
        cast.pitch = proj.pitch;
        cast.blockData = proj.blockData;
        cast.charged = consumeCharge(proj.player);

        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            double r = i == 0 ? 0.0 : ringRadius;
            Location probe = center.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            Location ground = findGround(probe);
            if (ground == null) continue;

            Spike spike = new Spike(ground);
            spike.spawnTick = i * stagger;
            spike.launchTick = spike.spawnTick + cast.riseTicks;
            spike.retractTick = spike.launchTick + cast.holdTicks;
            spike.removeTick = spike.retractTick + cast.fallTicks;
            cast.spikes.add(spike);
        }
        if (cast.spikes.isEmpty()) return;

        casts.put(proj.player.getUniqueId(), cast);
        MessageUtils.sendActionBar(proj.player, MSG_PREFIX + "&b地刺喷发 &7· "
                + (cast.charged ? "&e雷光加持" : "&f刺穿并掀飞范围内的敌人"));

        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null) return;
        cast.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (tickCast(cast)) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 推进一帧地刺喷发；返回 true 表示全部结束。 */
    private boolean tickCast(SpikeCast cast) {
        cast.tick++;
        Player player = cast.player;
        if (player == null || !player.isOnline() || player.isDead()) {
            finishCast(cast, true);
            return true;
        }

        boolean allDone = true;
        for (Spike spike : cast.spikes) {
            if (spike.removed) continue;
            allDone = false;

            if (!spike.spawned && cast.tick >= spike.spawnTick) {
                spike.spawned = true;
                spawnSpikeVisual(cast, spike);
            }
            if (!spike.launched && cast.tick >= spike.launchTick) {
                spike.launched = true;
                strikeSpike(cast, spike);
            }
            if (!spike.retracting && cast.tick >= spike.retractTick) {
                spike.retracting = true;
                retractSpike(cast, spike);
            }
            if (cast.tick >= spike.removeTick) {
                removeSpikeVisual(spike);
                spike.removed = true;
            }
        }

        if (allDone) {
            finishCast(cast, false);
            return true;
        }
        return false;
    }

    private void finishCast(SpikeCast cast, boolean removeVisuals) {
        if (cast == null) return;
        if (cast.task != null) {
            cast.task.cancel();
            cast.task = null;
        }
        if (removeVisuals) {
            for (Spike spike : cast.spikes) {
                removeSpikeVisual(spike);
                spike.removed = true;
            }
        }
        if (cast.player != null) {
            casts.remove(cast.player.getUniqueId(), cast);
        }
    }

    // ------------------------------------------------------------------
    // 地刺：破土 → 伤害 + 掀飞 → 缩回
    // ------------------------------------------------------------------

    private void spawnSpikeVisual(SpikeCast cast, Spike spike) {
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

        ParticleUtils.blockDust(ground.clone().add(0, 0.15, 0), groundBlockData(ground), 12);
        SoundUtils.play(ground, cast.sound, cast.volume * 0.8f, clampPitch(cast.pitch * 0.75f));
        if (cast.charged) {
            // 雷力：地刺破土时溅起电火花
            ParticleUtils.spawn(ground.clone().add(0, 1.0, 0), Particle.ELECTRIC_SPARK, 10, 0.25, 0.3, 0.25, 0.05);
        }
    }

    /** 地刺破土：范围内敌人吃一次伤害并被掀飞（自己只被掀飞、不吃自己的伤害）。 */
    private void strikeSpike(SpikeCast cast, Spike spike) {
        Player player = cast.player;
        Location ground = spike.ground;
        World world = ground.getWorld();
        if (player == null || world == null) return;

        double radius = cast.launchRadius;
        for (Entity raw : world.getNearbyEntities(ground, radius, radius, radius)) {
            if (!(raw instanceof LivingEntity target) || target.isDead() || !target.isValid()) continue;

            boolean self = target.equals(player);
            if (!self) {
                // 只打可攻击目标：队友 / 宠物 / NPC / 盔甲架不会被误伤
                if (!TargetFilter.isAttackable(player, target)) continue;
            }
            if (!cast.affected.add(target.getUniqueId())) continue;

            if (!self) {
                cast.ctx.damageCustom(target, cast.damage);
            }
            launch(target, ground, cast);
            if (target instanceof Player affected && !self) {
                // 交互反馈：被打到的玩家也能立刻知道发生了什么
                MessageUtils.sendActionBar(affected, MSG_PREFIX + "&c你被地刺穿了个正着");
            }
        }
        if (cast.charged && EffectSettings.effectsEnabled()) {
            ParticleUtils.spawn(ground.clone().add(0, 1.2, 0), Particle.ELECTRIC_SPARK, 6, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /** 把目标向上掀飞（有水平推力时同时向外推）。 */
    private void launch(LivingEntity target, Location from, SpikeCast cast) {
        if (target == null) return;
        if (cast.push > 0) {
            DisplacementUtils.knockback(target, from, cast.push, cast.uppercut);
        } else if (cast.uppercut != 0) {
            Vector velocity = target.getVelocity();
            target.setVelocity(velocity.setY(Math.max(velocity.getY(), cast.uppercut)));
        }
        target.setFallDistance(0);
        if (EffectSettings.effectsEnabled()) {
            ParticleUtils.spawn(target.getLocation().add(0, 0.4, 0), cast.particle, 6, 0.2, 0.25, 0.2, 0.08);
        }
    }

    private void retractSpike(SpikeCast cast, Spike spike) {
        BlockDisplay display = spike.display;
        if (display == null || !display.isValid()) return;
        display.setInterpolationDuration(cast.fallTicks);
        display.setTransformation(spikeTransform(cast.scale, -cast.scale * 1.4f));
    }

    private void removeSpikeVisual(Spike spike) {
        if (spike != null && spike.display != null && spike.display.isValid()) {
            spike.display.remove();
        }
        if (spike != null) {
            spike.display = null;
        }
    }

    // ------------------------------------------------------------------
    // Shift+右键：雷霆召唤（锁定多目标）
    // ------------------------------------------------------------------

    @Override
    public void onShiftRightClick(WeaponContext ctx) {
        Player player = ctx.player();
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (player == null || plugin == null) return;
        World world = player.getWorld();
        if (world == null) return;
        if (lightningCasts.containsKey(player.getUniqueId())) return;

        var stats = ctx.stats();
        int strikes = Math.max(1, Math.min(stats.lightningStrikes(), MAX_STRIKES));
        int glowTicks = Math.max(0, stats.lightningGlowTicks());
        double radius = Math.max(0.5, Math.min(stats.aoeRadius(), MAX_EFFECT_RADIUS));
        int stagger = (int) Math.max(1, Math.min(stats.hitIntervalTicks(), 10.0));

        // 落点兜底：锁定不到目标时，落雷打在准星地面
        Location center = findGround(ctx.aimLocation());
        if (center == null) {
            Location ahead = player.getEyeLocation().add(
                    player.getEyeLocation().getDirection().multiply(Math.min(stats.aimRange(), MAX_CAST_RANGE)));
            center = findGround(ahead);
        }
        if (center == null) {
            center = player.getLocation().clone();
        }

        // 锁定目标：主目标（准星/辅助瞄准）优先，其余按距离补满道数
        List<LivingEntity> locked = collectLockedTargets(ctx, Math.min(strikes, MAX_LOCK_TARGETS));

        LightningCast cast = new LightningCast(ctx, player);
        cast.center = center;
        cast.strikes = strikes;
        cast.stagger = stagger;
        cast.glowTicks = glowTicks;
        cast.radius = radius;
        cast.damage = stats.damage() * Math.max(0.0, stats.lightningDamageMultiplier());
        cast.lockedTargets.addAll(locked);

        // 雷力：下一发地刺附带雷光（窗口至少 1 秒）
        long windowMs = Math.max(1000L, glowTicks * 50L);
        chargedUntil.put(player.getUniqueId(), System.currentTimeMillis() + windowMs);

        MessageUtils.sendActionBar(player, MSG_PREFIX + "&e雷霆召唤 &7· "
                + (locked.isEmpty() ? "&f落雷降临" : "&c锁定 &f" + locked.size() + " &7个目标"));
        if (EffectSettings.effectsEnabled()) {
            SoundUtils.play(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.9f);
        }

        final UUID id = player.getUniqueId();
        cast.task = new BukkitRunnable() {
            private int tick = -1;

            @Override
            public void run() {
                tick++;
                if (!player.isOnline() || player.isDead()) {
                    lightningCasts.remove(id);
                    cancel();
                    return;
                }
                if (tick % cast.stagger == 0) {
                    int index = tick / cast.stagger;
                    if (index < cast.strikes) {
                        strike(cast, index);
                    }
                }
                if (tick >= (cast.strikes - 1) * cast.stagger) {
                    lightningCasts.remove(id);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        lightningCasts.put(id, cast);
    }

    /** 单道落雷：优先落在锁定的第 index 个目标头上，否则围绕准星落点补位。 */
    private void strike(LightningCast cast, int index) {
        Location loc = strikePosition(cast, index);
        World world = loc.getWorld();
        if (world == null) return;

        if (EffectSettings.effectsEnabled()) {
            // 服务端真实雷击特效：有闪电模型与雷声，但不会点火
            world.strikeLightningEffect(loc);
            ParticleUtils.ring(loc.clone().add(0, 0.15, 0), Math.max(0.5, cast.radius * 0.8),
                    Particle.ELECTRIC_SPARK, 20, 0.05);
            ParticleUtils.spawn(loc.clone().add(0, 1.0, 0), Particle.END_ROD, 10, 0.3, 0.4, 0.3, 0.02);
            SoundUtils.play(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.1f);
        }

        double radius = cast.radius;
        for (Entity raw : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(raw instanceof LivingEntity target) || target.isDead() || !target.isValid()) continue;
            if (!TargetFilter.isAttackable(cast.player, target)) continue;
            // 一次雷霆只结算一次伤害（多道落雷不叠伤）
            if (cast.damaged.add(target.getUniqueId())) {
                cast.ctx.damageCustom(target, cast.damage);
            }
            if (cast.glowTicks > 0) {
                PotionUtils.glowing(target, cast.glowTicks);
            }
            if (EffectSettings.effectsEnabled()) {
                ParticleUtils.spawn(target.getLocation().add(0, 1.0, 0),
                        Particle.ELECTRIC_SPARK, 6, 0.2, 0.3, 0.2, 0.05);
            }
        }
    }

    /** 第 index 道雷的落点：有锁定目标就打目标脚下，没有就围绕准星落点环形补位。 */
    private static Location strikePosition(LightningCast cast, int index) {
        if (index < cast.lockedTargets.size()) {
            LivingEntity target = cast.lockedTargets.get(index);
            if (target != null && target.isValid() && !target.isDead() && target.getWorld() != null) {
                Location ground = findGround(target.getLocation());
                return ground != null ? ground : target.getLocation();
            }
        }
        Location center = cast.center;
        if (center == null) {
            center = cast.player.getLocation().clone();
        }
        if (cast.strikes <= 1) {
            return center.clone();
        }
        double angle = (Math.PI * 2.0 / cast.strikes) * index;
        double ringRadius = Math.max(0.8, cast.radius * 0.6);
        return center.clone().add(Math.cos(angle) * ringRadius, 0, Math.sin(angle) * ringRadius);
    }

    /** 取出并消耗「雷力」。 */
    private boolean consumeCharge(Player player) {
        if (player == null) return false;
        Long until = chargedUntil.remove(player.getUniqueId());
        return until != null && System.currentTimeMillis() <= until;
    }

    // ------------------------------------------------------------------
    // 锁定目标
    // ------------------------------------------------------------------

    /** 锁定主目标：准星直指 > 锥形辅助瞄准；要求存活、同世界、在 aim-range 内且可攻击。 */
    private LivingEntity lockTarget(WeaponContext ctx) {
        if (ctx == null || ctx.player() == null) return null;
        // 准星直指的目标可能不可攻击（队友 / 宠物 / NPC），此时回退到锥形辅助瞄准
        LivingEntity target = ctx.aimTarget();
        if (!canLock(ctx, target)) {
            target = ctx.aimAssist();
        }
        return canLock(ctx, target) ? target : null;
    }

    /** 目标是否可被锁定：存活、同世界、在 aim-range 内、且通过 TargetFilter。 */
    private static boolean canLock(WeaponContext ctx, LivingEntity target) {
        if (ctx == null || ctx.player() == null || target == null) return false;
        if (target.isDead() || !target.isValid()) return false;
        Player player = ctx.player();
        if (target.getWorld() != player.getWorld()) return false;
        double range = Math.max(2.0, Math.min(ctx.stats().aimRange(), MAX_CAST_RANGE));
        if (player.getLocation().distanceSquared(target.getLocation()) > range * range) return false;
        return TargetFilter.isAttackable(player, target);
    }

    /**
     * 收集锁定目标：主目标优先，其余在 aim-range 内按「离玩家距离」补满，
     * 供 Shift+右键的多道落雷逐个索敌。
     */
    private List<LivingEntity> collectLockedTargets(WeaponContext ctx, int limit) {
        List<LivingEntity> result = new ArrayList<>();
        if (ctx == null || ctx.player() == null || limit <= 0) return result;
        Player player = ctx.player();
        var stats = ctx.stats();
        double range = Math.max(2.0, Math.min(stats.aimRange(), MAX_CAST_RANGE));

        LivingEntity primary = lockTarget(ctx);
        if (primary != null) {
            result.add(primary);
        }

        List<LivingEntity> candidates = new ArrayList<>();
        World world = player.getWorld();
        if (world == null) return result;
        for (Entity raw : world.getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(raw instanceof LivingEntity target) || target.isDead() || !target.isValid()) continue;
            if (result.contains(target)) continue;
            if (!TargetFilter.isAttackable(player, target)) continue;
            if (player.getLocation().distanceSquared(target.getLocation()) > range * range) continue;
            candidates.add(target);
        }
        candidates.sort(Comparator.comparingDouble(t -> player.getLocation().distanceSquared(t.getLocation())));
        for (LivingEntity candidate : candidates) {
            if (result.size() >= limit) break;
            result.add(candidate);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 弹射物表现 / 工具
    // ------------------------------------------------------------------

    private BlockDisplay spawnProjectileDisplay(Projectile proj) {
        if (!EffectSettings.effectsEnabled()) return null;
        Location at = proj.position.clone();
        World world = at.getWorld();
        if (world == null) return null;
        return world.spawn(at, BlockDisplay.class, d -> {
            d.setBlock(proj.blockData);
            d.setPersistent(false);
            d.setGravity(false);
            d.setInvulnerable(true);
            d.setShadowRadius(0);
            d.setShadowStrength(0);
            d.addScoreboardTag(DISPLAY_TAG);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setInterpolationDelay(0);
            d.setTransformation(projectileTransform(proj.direction, 1.0f));
        });
    }

    /** 弹射物外观：把「朝上」的尖滴石旋转到飞行方向。 */
    private static Transformation projectileTransform(Vector direction, float scale) {
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                facingQuaternion(direction),
                new Vector3f(scale, scale, scale),
                new Quaternionf(0f, 0f, 0f, 1f));
    }

    /** 由「朝上」旋转到 direction 的四元数。 */
    private static Quaternionf facingQuaternion(Vector direction) {
        Vector dir = direction == null ? new Vector(0, 1, 0) : direction.clone();
        if (dir.lengthSquared() < 1.0e-8) {
            return new Quaternionf();
        }
        dir.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, dir.getY()));
        if (dot >= 1.0 - 1.0e-6) {
            return new Quaternionf();
        }
        if (dot <= -1.0 + 1.0e-6) {
            return new Quaternionf().rotateX((float) Math.PI);
        }
        double angle = Math.acos(dot);
        Vector axis = new Vector(dir.getZ(), 0, -dir.getX());
        if (axis.lengthSquared() < 1.0e-8) {
            return new Quaternionf();
        }
        axis.normalize();
        return new Quaternionf().rotateAxis((float) angle,
                (float) axis.getX(), (float) axis.getY(), (float) axis.getZ());
    }

    private static Transformation spikeTransform(float scale, float yOffset) {
        return new Transformation(
                new Vector3f(0f, yOffset, 0f),
                new Quaternionf(0f, 0f, 0f, 1f),
                new Vector3f(scale, scale, scale),
                new Quaternionf(0f, 0f, 0f, 1f));
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
        // 未加载区块的 getBlockAt 会同步加载区块（主线程卡顿），直接跳过
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;

        int startY = Math.min(world.getMaxHeight() - 2, probe.getBlockY() + 1);
        int endY = Math.max(world.getMinHeight() + 1, probe.getBlockY() - 6);
        for (int y = startY; y >= endY; y--) {
            Block below = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            if (below.getType().isSolid() && !feet.getType().isSolid()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    /** 地面方块数据（区块未加载 / 读取失败时退回尖滴石）。 */
    private static BlockData groundBlockData(Location ground) {
        World world = ground.getWorld();
        if (world != null && world.isChunkLoaded(ground.getBlockX() >> 4, ground.getBlockZ() >> 4)) {
            try {
                return ground.getBlock().getBlockData();
            } catch (RuntimeException ignored) {
                // 区块刚好卸载：退回尖滴石，不影响后续逻辑
            }
        }
        return spikeBlockData();
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

    /** 找出弹射物落点附近的第一个活体目标（排除施法者自己）。 */
    private static LivingEntity findEntity(Location location, double radius, Player caster) {
        World world = location.getWorld();
        if (world == null) return null;
        for (Entity raw : world.getNearbyEntities(location, radius, radius, radius)) {
            if (!(raw instanceof LivingEntity target)) continue;
            if (target.isDead() || !target.isValid()) continue;
            if (caster != null && target.equals(caster)) continue;
            return target;
        }
        return null;
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
        if (name == null || name.isBlank()) return Sound.BLOCK_POINTED_DRIPSTONE_BREAK;
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Sound.BLOCK_POINTED_DRIPSTONE_BREAK;
        }
    }

    /** 音调保持在 Bukkit 可用范围（0.5~2.0）。 */
    private static float clampPitch(float pitch) {
        if (!Float.isFinite(pitch)) return 1.0f;
        return Math.max(0.5f, Math.min(pitch, 2.0f));
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void cleanup() {
        for (Projectile proj : projectiles.values()) {
            cancelProjectile(proj);
        }
        projectiles.clear();
        for (SpikeCast cast : casts.values()) {
            finishCast(cast, true);
        }
        casts.clear();
        for (LightningCast cast : lightningCasts.values()) {
            if (cast.task != null) {
                cast.task.cancel();
            }
        }
        lightningCasts.clear();
        chargedUntil.clear();
    }

    @Override
    public void onQuit(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        Projectile proj = projectiles.remove(id);
        if (proj != null) {
            cancelProjectile(proj);
        }
        SpikeCast cast = casts.get(id);
        if (cast != null) {
            finishCast(cast, true);
        }
        LightningCast lightning = lightningCasts.remove(id);
        if (lightning != null && lightning.task != null) {
            lightning.task.cancel();
        }
        chargedUntil.remove(id);
    }

    private void cancelProjectile(Projectile proj) {
        if (proj == null) return;
        if (proj.task != null) {
            proj.task.cancel();
            proj.task = null;
        }
        if (proj.display != null && proj.display.isValid()) {
            proj.display.remove();
        }
        proj.display = null;
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /** 飞行中的地刺弹射物。 */
    private static final class Projectile {
        final WeaponContext ctx;
        final Player player;
        final World world;
        Vector direction;
        Location position;
        LivingEntity target;
        BlockDisplay display;
        BukkitTask task;
        double speed;
        double maxRange;
        double launchRadius;
        double damage;
        double uppercut;
        double push;
        double traveled;
        int ticks;
        Particle particle;
        Sound sound;
        float volume;
        float pitch;
        BlockData blockData;

        Projectile(WeaponContext ctx, Player player, World world, Location position, Vector direction) {
            this.ctx = ctx;
            this.player = player;
            this.world = world;
            this.position = position;
            this.direction = direction;
        }
    }

    /** 单根地刺的时间轴与展示实体。 */
    private static final class Spike {
        final Location ground;
        BlockDisplay display;
        int spawnTick;
        int launchTick;
        int retractTick;
        int removeTick;
        boolean spawned;
        boolean launched;
        boolean retracting;
        boolean removed;

        Spike(Location ground) {
            this.ground = ground;
        }
    }

    /** 一轮地刺喷发的共享参数。 */
    private static final class SpikeCast {
        final WeaponContext ctx;
        final Player player;
        final List<Spike> spikes = new ArrayList<>();
        final Set<UUID> affected = new HashSet<>();
        BukkitTask task;
        int tick;
        double launchRadius;
        double damage;
        double uppercut;
        double push;
        int riseTicks;
        int holdTicks;
        int fallTicks;
        float scale;
        float volume;
        float pitch;
        boolean charged;
        Particle particle;
        Sound sound;
        BlockData blockData;

        SpikeCast(WeaponContext ctx, Player player) {
            this.ctx = ctx;
            this.player = player;
        }
    }

    /** 一轮雷霆召唤的共享参数（锁定目标 + 伤害去重）。 */
    private static final class LightningCast {
        final WeaponContext ctx;
        final Player player;
        final List<LivingEntity> lockedTargets = new ArrayList<>();
        final Set<UUID> damaged = new HashSet<>();
        Location center;
        BukkitTask task;
        int strikes;
        int stagger;
        int glowTicks;
        double radius;
        double damage;

        LightningCast(WeaponContext ctx, Player player) {
            this.ctx = ctx;
            this.player = player;
        }
    }
}
