package com.skillcore.weapon.skills;

import com.skillcore.SkillCorePlugin;
import com.skillcore.effect.EffectSettings;
import com.skillcore.effect.Effects;
import com.skillcore.hook.CraftEngineHook;
import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.MessageUtils;
import com.skillcore.utils.TargetFilter;
import com.skillcore.weapon.AbstractWeaponSkill;
import com.skillcore.weapon.SkillWeapon;
import com.skillcore.weapon.WeaponContext;
import com.skillcore.weapon.WeaponItems;
import com.skillcore.weapon.annotation.WeaponSkillInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 血月之镰 · 右键「血月回旋」。
 * <p>
 * 掷出一把旋转的镰刀（{@link ItemDisplay} 真实显示物，不是纯粒子假弹道）：
 * <ol>
 *   <li><b>锁敌</b>：优先准星射线目标，其次锥形辅助瞄准；锁到目标后按
 *       {@code turn-rate}（度/tick）限速转向，并按目标速度做提前量预判，会拐弯追人；</li>
 *   <li><b>去程</b>：沿飞行方向穿透，最多命中 {@code max-targets} 个目标，
 *       每个吃 {@code damage × throw-damage-ratio}，命中挂「血月印记」并僵直；</li>
 *   <li><b>折返</b>：飞满 {@code throw-range} / 命中上限 / 撞墙后掉头飞回玩家，
 *       回程命中吃 {@code damage × return-damage-ratio}，追上玩家后收刀。</li>
 * </ol>
 * 表现层在 {@link BloodMoonScytheFx}：起手爆环、飞行拖尾 + 垂直刀环、命中白闪、
 * 折返点与回收都有独立分镜。显示物带 {@code skillcore_scythe_throw} tag、
 * 非持久化，插件禁用 / 重载 / 玩家退出时由 {@link #cleanup()} / {@link #onQuit(Player)} 回收。
 */
@WeaponSkillInfo(id = "BLOOD_MOON_THROW", aliases = {"BLOODMOON", "SCYTHE_THROW"},
        description = "掷出旋转的血月镰，锁敌追击后折返")
public final class BloodMoonThrowSkill extends AbstractWeaponSkill {

    /** 显示物 tag（插件禁用时兜底清理用）。 */
    private static final String DISPLAY_TAG = "skillcore_scythe_throw";
    /** 单次飞行最大存在时间（tick），防止任何异常导致实体/任务长期残留。 */
    private static final int MAX_LIFETIME_TICKS = 20 * 10;

    private final Map<UUID, Flight> flights = new ConcurrentHashMap<>();

    public BloodMoonThrowSkill() {
        super("BLOOD_MOON_THROW");
    }

    /** 一次飞行的运行时状态。 */
    private static final class Flight {
        Player player;
        WeaponContext ctx;
        ItemDisplay display;
        ItemStack visual;
        BukkitTask task;
        Location location;
        Vector direction;
        LivingEntity target;
        boolean returning;
        int ticks;
        int outTicks;
        int hitCount;
        int maxOutTicks;
        /** 命中顿帧：命中瞬间把飞行停 2 tick，模拟「砍进肉里」的打击感。 */
        int hitStopTicks;
        float spin;
        final Set<UUID> hit = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void onRightClick(WeaponContext ctx) {
        if (ctx == null || ctx.player() == null || ctx.weapon() == null) return;
        SkillCorePlugin plugin = SkillCorePlugin.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        Player player = ctx.player();

        // 同一玩家只允许一把飞行中的镰刀：重复释放先静默收回旧的
        cancelFlight(player.getUniqueId(), false);

        LivingEntity target = resolveTarget(ctx, player);
        Location eye = player.getEyeLocation();
        Vector direction;
        if (target != null) {
            direction = target.getLocation()
                    .add(0, Math.max(0.5, target.getHeight() * 0.5), 0)
                    .toVector().subtract(eye.toVector());
        } else {
            direction = eye.getDirection().clone();
        }
        if (direction.lengthSquared() < 1.0e-6) {
            direction = eye.getDirection().clone();
        }
        if (direction.lengthSquared() < 1.0e-6) {
            direction = new Vector(0, 0, 1);
        }
        direction.normalize();

        Flight flight = new Flight();
        flight.player = player;
        flight.ctx = ctx;
        flight.target = target;
        flight.direction = direction.clone();
        flight.location = eye.clone().add(direction.clone().multiply(0.8));
        if (flight.location.getWorld() == null) return;
        flight.maxOutTicks = (int) Math.max(2.0,
                Math.ceil(ctx.stats().throwRange() / Math.max(0.05, ctx.stats().throwSpeed())));

        if (EffectSettings.effectsEnabled()) {
            flight.visual = resolveVisual(ctx);
            if (flight.visual != null) {
                flight.display = spawnDisplay(flight.location, flight.visual);
            }
        }

        player.swingMainHand();
        BloodMoonScytheFx.castBurst(eye, direction);
        sendCastMessage(player, target);
        if (target != null) {
            BloodMoonScytheFx.lockMarker(target.getLocation());
        }

        flight.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick(flight);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        flights.put(player.getUniqueId(), flight);
    }

    // ------------------------------------------------------------------
    // 选目标 / 显示物
    // ------------------------------------------------------------------

    /** 准星目标优先；没有则用锥形辅助瞄准补一个（贴合 mcskills 的「锁敌」手感）。 */
    private static LivingEntity resolveTarget(WeaponContext ctx, Player player) {
        var stats = ctx.stats();
        LivingEntity target = ctx.target();
        if (isValidTarget(player, target)) {
            return target;
        }
        target = AimUtils.getBestConeTarget(player, stats.aimAngle(), stats.aimRange());
        return isValidTarget(player, target) ? target : null;
    }

    private static boolean isValidTarget(Player player, LivingEntity target) {
        if (target == null || target.equals(player)) return false;
        if (!target.isValid() || target.isDead() || target.getWorld() != player.getWorld()) return false;
        return TargetFilter.isAttackable(player, target);
    }

    private static ItemStack resolveVisual(WeaponContext ctx) {
        ItemStack held = ctx.item();
        if (held != null && held.getType() != Material.AIR) {
            return held.clone();
        }
        SkillWeapon weapon = ctx.weapon();
        if (weapon == null) return null;
        if (weapon.hasCraftEngineModel()) {
            ItemStack ce = CraftEngineHook.buildItem(weapon.craftEngineModel());
            if (ce != null) return ce;
        }
        return new ItemStack(weapon.material());
    }

    private static ItemDisplay spawnDisplay(Location location, ItemStack visual) {
        World world = location.getWorld();
        if (world == null || visual == null) return null;
        try {
            return world.spawn(location, ItemDisplay.class, display -> {
                display.setItemStack(visual.clone());
                display.setInvulnerable(true);
                display.setGravity(false);
                display.setPersistent(false);
                display.setShadowRadius(0);
                display.setShadowStrength(0);
                display.addScoreboardTag(DISPLAY_TAG);
                display.setTeleportDuration(1);
                display.setInterpolationDuration(1);
                display.setInterpolationDelay(0);
                display.setTransformation(spinTransform(0f));
            });
        } catch (Exception ex) {
            // 显示物生成失败（例如实体上限）不应该让技能整体报错：返回 null，只保留粒子轨迹
            return null;
        }
    }

    private static Transformation spinTransform(float spinDegrees) {
        Quaternionf rotation = new Quaternionf()
                .rotationY((float) Math.toRadians(spinDegrees))
                .mul(new Quaternionf().rotationZ((float) Math.toRadians(65)));
        float scale = 1.6f;
        return new Transformation(new Vector3f(0, 0, 0), rotation,
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    // ------------------------------------------------------------------
    // 每 tick 推进
    // ------------------------------------------------------------------

    private void tick(Flight flight) {
        if (flight == null) return;
        Player player = flight.player;
        if (player == null || !player.isOnline() || player.isDead()
                || player.getWorld() == null || flight.location.getWorld() == null
                || !player.getWorld().equals(flight.location.getWorld())) {
            finish(flight, false);
            return;
        }
        // 换手 / 切武器 / 收进背包：镰刀立刻归鞘
        String held = WeaponItems.getHeldWeaponId(SkillCorePlugin.getInstance(), player);
        if (held == null || !held.equalsIgnoreCase(flight.ctx.weapon().id())) {
            finish(flight, false);
            return;
        }
        if (flight.display != null && !flight.display.isValid()) {
            finish(flight, false);
            return;
        }
        if (flight.ticks++ >= MAX_LIFETIME_TICKS) {
            finish(flight, false);
            return;
        }

        // 命中顿帧：命中瞬间停 2 tick 不前进，只保留旋转/粒子，强化打击感
        if (flight.hitStopTicks > 0) {
            flight.hitStopTicks--;
            render(flight);
            return;
        }

        var stats = flight.ctx.stats();
        if (!flight.returning) {
            stepOutbound(flight, stats);
        } else if (stepReturn(flight, stats)) {
            // 已收回：任务在 finish 里取消，不要再更新残余特效
            return;
        }
        render(flight);
    }

    /** 显示物跟随 + 粒子分镜（正常飞行与命中顿帧期间都会调用）。 */
    private void render(Flight flight) {
        Player player = flight.player;
        // 显示物跟随 + 旋转
        if (flight.display != null && flight.display.isValid()) {
            flight.display.teleport(flight.location.clone());
            flight.spin += 38f;
            if (flight.spin > 360f) flight.spin -= 360f;
            flight.display.setTransformation(spinTransform(flight.spin));
        }
        // 粒子分镜
        BloodMoonScytheFx.flightTick(flight.location, flight.direction, flight.ticks);
        if (flight.ticks % 3 == 0) {
            // 短命残影：每 3 tick 留一个 4 tick 的镰刀影子，强化高速旋转的观感
            if (EffectSettings.effectsEnabled() && flight.visual != null) {
                Effects.display(flight.visual, 1.5f, 4).play(flight.location);
            }
        }
        if (!flight.returning && flight.ticks % 4 == 0 && isValidTarget(player, flight.target)) {
            BloodMoonScytheFx.lockMarker(flight.target.getLocation());
        }
    }

    /** 去程：锁敌转向 → 前进 → 命中/撞墙/到距离判定。 */
    private void stepOutbound(Flight flight, com.skillcore.weapon.WeaponStats stats) {
        if (isValidTarget(flight.player, flight.target)) {
            // 提前量：按「距离 / 飞行速度」估算飞行 tick，再叠加目标速度
            Location aim = flight.target.getEyeLocation().clone();
            double travelTicks = flight.location.distance(aim) / Math.max(0.05, stats.throwSpeed());
            aim.add(flight.target.getVelocity().clone().multiply(Math.min(travelTicks, 10.0)));
            Vector desired = aim.toVector().subtract(flight.location.toVector());
            if (desired.lengthSquared() > 1.0e-6) {
                flight.direction = turnTowards(flight.direction, desired.normalize(), stats.turnRate());
            }
        }
        flight.location.add(flight.direction.clone().multiply(stats.throwSpeed()));
        flight.outTicks++;

        if (isSolidBlock(flight.location)) {
            startReturn(flight);
            return;
        }
        hitEntities(flight, stats.throwDamageRatio(), true);
        if (flight.hitCount >= stats.maxTargets() || flight.outTicks >= flight.maxOutTicks) {
            startReturn(flight);
        }
    }

    /** 回程：追着玩家飞回来，路径上的新目标也吃伤害（同一目标只吃一次）。 */
    private boolean stepReturn(Flight flight, com.skillcore.weapon.WeaponStats stats) {
        Location to = flight.player.getEyeLocation().clone().subtract(0, 0.2, 0);
        Vector desired = to.toVector().subtract(flight.location.toVector());
        double distance = desired.length();
        if (distance < 1.4) {
            finish(flight, true);
            return true;
        }
        if (distance > 64.0) {
            // 玩家瞬移/跨维度等异常情况：别让镰刀横穿整个世界飞回来
            finish(flight, false);
            return true;
        }
        flight.direction = desired.normalize();
        double step = Math.min(stats.returnSpeed(), distance);
        flight.location.add(flight.direction.clone().multiply(step));
        hitEntities(flight, stats.returnDamageRatio(), false);
        return false;
    }

    private void startReturn(Flight flight) {
        if (flight.returning) return;
        flight.returning = true;
        BloodMoonScytheFx.turnPoint(flight.location);
    }

    // ------------------------------------------------------------------
    // 命中判定
    // ------------------------------------------------------------------

    private void hitEntities(Flight flight, double damageRatio, boolean mark) {
        World world = flight.location.getWorld();
        if (world == null) return;
        List<Entity> nearby = new java.util.ArrayList<>(world.getNearbyEntities(flight.location, 1.3, 1.3, 1.3,
                entity -> entity instanceof LivingEntity && !entity.equals(flight.player)));
        for (Entity raw : nearby) {
            if (!(raw instanceof LivingEntity victim)) continue;
            if (!victim.isValid() || victim.isDead()) continue;
            if (!TargetFilter.isAttackable(flight.player, victim)) continue;
            // 每次投掷同一个目标只结算一次（去程命中过的回程不再重复扣血）
            if (!flight.hit.add(victim.getUniqueId())) continue;

            double dealt = flight.ctx.damageCustom(victim, flight.ctx.stats().damage() * damageRatio);
            if (dealt <= 0) continue;
            flight.hitCount++;
            flight.hitStopTicks = Math.max(flight.hitStopTicks, 2);

            BloodMoonScytheFx.impact(victim.getLocation().add(0, victim.getHeight() * 0.5, 0), victim);
            DisplacementUtils.knockback(victim, flight.location, 0.18, 0.06);
            if (victim instanceof Player victimPlayer) {
                // 交互反馈：被打到的玩家也能立刻知道发生了什么
                MessageUtils.sendActionBar(victimPlayer, "&8[&c血月&8] &7血月镰刃划过了你");
            }
            if (mark) {
                BloodMoonMarks.apply(victim, flight.ctx.stats().markDurationTicks());
                BloodMoonScytheFx.markApplied(victim.getLocation());
            }
        }
    }

    // ------------------------------------------------------------------
    // 收尾 / 生命周期
    // ------------------------------------------------------------------

    private void finish(Flight flight, boolean caught) {
        if (flight == null) return;
        if (flight.task != null) {
            flight.task.cancel();
            flight.task = null;
        }
        if (flight.display != null) {
            if (flight.display.isValid()) flight.display.remove();
            flight.display = null;
        }
        Player player = flight.player;
        if (player != null) {
            flights.remove(player.getUniqueId(), flight);
            if (caught && player.isOnline() && !player.isDead()) {
                BloodMoonScytheFx.recall(player.getEyeLocation());
                MessageUtils.sendActionBar(player, "&8[&c血月&8] &7镰刀已归鞘");
            }
        }
    }

    private void cancelFlight(UUID playerId, boolean caught) {
        if (playerId == null) return;
        Flight flight = flights.remove(playerId);
        if (flight != null) finish(flight, caught);
    }

    @Override
    public void cleanup() {
        for (Flight flight : flights.values()) {
            if (flight == null) continue;
            if (flight.task != null) {
                flight.task.cancel();
                flight.task = null;
            }
            if (flight.display != null) {
                if (flight.display.isValid()) flight.display.remove();
                flight.display = null;
            }
        }
        flights.clear();
        BloodMoonMarks.clear();
    }

    @Override
    public void onQuit(Player player) {
        if (player == null) return;
        cancelFlight(player.getUniqueId(), false);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 按最大转向角把 current 转向 desired（度/tick 限速）。 */
    private static Vector turnTowards(Vector current, Vector desired, double maxDegPerTick) {
        Vector from = current.clone().normalize();
        Vector to = desired.clone().normalize();
        double angle = from.angle(to);
        double maxRadians = Math.toRadians(Math.max(0.0, maxDegPerTick));
        if (!Double.isFinite(angle) || angle <= maxRadians || angle < 1.0e-6) {
            return to;
        }
        double t = maxRadians / angle;
        Vector result = from.multiply(1.0 - t).add(to.multiply(t));
        if (result.lengthSquared() < 1.0e-8) return to;
        return result.normalize();
    }

    /** 撞墙判定：只认实心方块（水/草/花不挡镰刀）。 */
    private static boolean isSolidBlock(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (location.getY() < location.getWorld().getMinHeight()
                || location.getY() > location.getWorld().getMaxHeight()) {
            return true;
        }
        return location.getBlock().getType().isSolid();
    }

    private static void sendCastMessage(Player player, LivingEntity target) {
        if (target == null) {
            MessageUtils.sendActionBar(player, "&8[&c血月&8] &7血月回旋");
        } else {
            MessageUtils.sendActionBar(player, "&8[&c血月&8] &7血月回旋 &8· &c锁定 &f"
                    + MessageUtils.strip(target.getName()));
        }
    }
}
