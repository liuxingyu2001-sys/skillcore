package com.skillcore.weapon;

import com.skillcore.effect.EffectContext;
import com.skillcore.effect.SkillEffect;
import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.EntityUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.PercentageDamageUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.SkillDamageUtils;
import com.skillcore.utils.SoundUtils;
import com.skillcore.utils.TargetFilter;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 武器技能执行上下文 — 写技能时的主入口。
 * <p>
 * 持有：施法者、武器、数值 {@link #stats()}、目标、以及一批直接调用工具类的快捷方法。
 * <pre>
 * // 例：利刃突刺
 * public void onRightClick(WeaponContext ctx) {
 *     ctx.dashForward();
 *     ctx.damageTarget();
 *     ctx.healSelfByDamage();
 * }
 * </pre>
 */
public final class WeaponContext {

    private final Player player;
    private final SkillWeapon weapon;
    private final WeaponStats stats;
    private LivingEntity target;
    private Location aimLocation;
    private final ItemStack item;
    private final boolean leftClick;
    private final boolean sneak;
    private double power = 1.0;

    public WeaponContext(
            Player player,
            SkillWeapon weapon,
            LivingEntity target,
            Location aimLocation,
            ItemStack item,
            boolean leftClick,
            boolean sneak
    ) {
        this.player = player;
        this.weapon = weapon;
        this.stats = weapon != null ? weapon.stats() : new WeaponStats();
        this.target = target;
        this.aimLocation = aimLocation;
        this.item = item;
        this.leftClick = leftClick;
        this.sneak = sneak;
    }

    // ------------------------------------------------------------------
    // 基础访问
    // ------------------------------------------------------------------

    public Player player() { return player; }
    public SkillWeapon weapon() { return weapon; }
    /** 武器数值 — 所有伤害/冷却/位移参数从这里读。 */
    public WeaponStats stats() { return stats; }
    public ItemStack item() { return item; }
    public boolean isLeftClick() { return leftClick; }
    public boolean isRightClick() { return !leftClick; }
    public boolean isSneaking() { return sneak; }
    public double power() { return power; }

    public WeaponContext power(double p) { this.power = p; return this; }

    public LivingEntity target() { return target; }
    public void target(LivingEntity t) { this.target = t; }
    public boolean hasTarget() {
        return target != null && DamageUtils.isAlive(target);
    }

    public Location aimLocation() {
        if (aimLocation != null) return aimLocation;
        return AimUtils.getAimLocation(player, stats.aimRange());
    }
    public void aimLocation(Location loc) { this.aimLocation = loc; }

    public Location eyeLocation() { return player.getEyeLocation(); }
    public Location playerLocation() { return player.getLocation(); }

    // ------------------------------------------------------------------
    // 瞄准
    // ------------------------------------------------------------------

    /** 射线拾取目标；已有目标则返回已有目标。 */
    public LivingEntity aimTarget() {
        if (hasTarget()) return target;
        LivingEntity hit = AimUtils.raycastEntity(player, stats.aimRange());
        if (hit != null) target = hit;
        return target;
    }

    /** 锥形最佳目标（辅助瞄准）。 */
    public LivingEntity aimAssist() {
        LivingEntity best = AimUtils.aimAssist(player, stats.aimRange(), stats.aimAngle());
        if (best != null) target = best;
        return target;
    }

    /** 最近敌人。 */
    public LivingEntity nearestEnemy() {
        LivingEntity nearest = AimUtils.getNearest(player, stats.range(), e -> EntityUtils.isEnemy(player, e));
        if (nearest != null) target = nearest;
        return target;
    }

    // ------------------------------------------------------------------
    // 伤害（自动吃武器数值）
    // ------------------------------------------------------------------

    /** 对当前目标打一次完整伤害（物理+百分比）。 */
    public double damageTarget() {
        return damage(target);
    }

    /** 对指定目标造成武器完整伤害。 */
    public double damage(LivingEntity victim) {
        if (!canDamage(victim)) return 0;
        double dealt = stats.calculateTotal(victim, power);
        if (dealt > 0) {
            SkillDamageUtils.damage(victim, dealt, player, skillSource());
            hitFx(victim);
            applyLifesteal(dealt);
        }
        return dealt;
    }

    /** 仅物理伤害（不吃百分比）。 */
    public double damagePhysical(LivingEntity victim) {
        if (!canDamage(victim)) return 0;
        double dealt = stats.calculateHit(victim, power);
        if (dealt > 0) {
            SkillDamageUtils.damage(victim, dealt, player, skillSource());
            applyLifesteal(dealt);
        }
        return dealt;
    }

    /** 仅百分比伤害。 */
    public double damagePercent(LivingEntity victim) {
        if (!canDamage(victim)) return 0;
        double dealt = stats.calculatePercentBonus(victim);
        if (dealt > 0) {
            SkillDamageUtils.damage(victim, dealt, player, skillSource());
            applyLifesteal(dealt);
        }
        return dealt;
    }

    /** 自定义基础伤害（覆盖 stats.damage）。 */
    public double damageCustom(LivingEntity victim, double baseDamage) {
        if (!canDamage(victim)) return 0;
        double dealt = DamageUtils.calculateFinalDamage(
                baseDamage, stats.damageScaling(), power,
                stats.criticalChance(), stats.criticalMultiplier(),
                stats.armorPenetration(), victim);
        if (dealt > 0) {
            SkillDamageUtils.damage(victim, dealt, player, skillSource());
            applyLifesteal(dealt);
        }
        return dealt;
    }

    /**
     * 单体伤害前的统一校验：活着的目标且不是「应忽略的目标」。
     * <p>
     * 直接命中虽然走的是射线/瞄准结果，但辅助瞄准、脚本化目标等路径仍可能拿到
     * 队友 / 宠物 / NPC / 盔甲架，这里再兜一层，避免绕过 {@code TargetFilter}。
     */
    private boolean canDamage(LivingEntity victim) {
        return DamageUtils.isAlive(victim) && !TargetFilter.shouldIgnoreTarget(player, victim);
    }

    /** 按武器 lifesteal 比例自动吸血（所有伤害统一入口）。 */
    private void applyLifesteal(double dealt) {
        if (dealt <= 0) return;
        // 盔甲套装的吸血对技能伤害同样生效：取较大值，避免与武器吸血叠加成双倍回血
        double percent = Math.max(stats.lifesteal(), armorLifesteal());
        if (percent > 0) {
            LifestealUtils.healByDamagePercent(player, dealt, percent);
        }
    }

    /** 当前穿戴整套盔甲的吸血加成（未穿戴 / 盔甲模块未就绪返回 0）。 */
    private double armorLifesteal() {
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin == null || plugin.getArmorManager() == null) {
            return 0.0;
        }
        return plugin.getArmorManager().getActive(player).lifesteal();
    }

    /** 当前技能来源 id（用于技能伤害标记）。 */
    public String skillSource() {
        return weapon != null ? weapon.id() : null;
    }

    /**
     * 多段连击（hit-count / hit-interval-ticks）。
     * 立即打第 1 段，其余用调度器。
     */
    public double multiHit(LivingEntity victim) {
        int count = Math.max(1, stats.hitCount());
        double first = damage(victim);
        if (count <= 1) return first;
        var plugin = com.skillcore.SkillCorePlugin.getInstance();
        if (plugin == null) return first;
        var scheduler = plugin.getServer().getScheduler();
        java.util.UUID playerId = player.getUniqueId();
        for (int i = 1; i < count; i++) {
            // 延迟段必须登记句柄：0 冷却连点 / 重载 / 玩家退出时要把它们取消，
            // 否则旧任务会带着旧 stats 继续结算伤害，且任务数会无限叠加。
            java.util.concurrent.atomic.AtomicReference<org.bukkit.scheduler.BukkitTask> ref =
                    new java.util.concurrent.atomic.AtomicReference<>();
            org.bukkit.scheduler.BukkitTask task = scheduler.runTaskLater(plugin, () -> {
                untrackPendingHit(playerId, ref.get());
                if (player.isOnline() && DamageUtils.isAlive(player) && DamageUtils.isAlive(victim)) {
                    damage(victim);
                }
            }, Math.max(0L, (long) (stats.hitIntervalTicks() * i)));
            ref.set(task);
            trackPendingHit(playerId, task);
        }
        return first;
    }

    /** 待结算的多段伤害任务（按玩家分组，退出/重载时统一取消）。 */
    private static final java.util.Map<java.util.UUID, java.util.List<org.bukkit.scheduler.BukkitTask>> PENDING_HITS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static void trackPendingHit(java.util.UUID playerId, org.bukkit.scheduler.BukkitTask task) {
        if (playerId == null || task == null) return;
        PENDING_HITS.computeIfAbsent(playerId, k -> java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                .add(task);
    }

    private static void untrackPendingHit(java.util.UUID playerId, org.bukkit.scheduler.BukkitTask task) {
        if (playerId == null) return;
        java.util.List<org.bukkit.scheduler.BukkitTask> list = PENDING_HITS.get(playerId);
        if (list == null) return;
        list.remove(task);
        if (list.isEmpty()) {
            PENDING_HITS.remove(playerId);
        }
    }

    /** 取消某玩家所有未结算的多段伤害（退出 / 死亡时调用）。 */
    public static void cancelPendingHits(java.util.UUID playerId) {
        if (playerId == null) return;
        java.util.List<org.bukkit.scheduler.BukkitTask> list = PENDING_HITS.remove(playerId);
        if (list == null) return;
        for (org.bukkit.scheduler.BukkitTask task : list) {
            if (task != null) task.cancel();
        }
        list.clear();
    }

    /** 取消全部未结算的多段伤害（重载 / 禁用时调用）。 */
    public static void cancelAllPendingHits() {
        for (java.util.UUID id : PENDING_HITS.keySet()) {
            cancelPendingHits(id);
        }
        PENDING_HITS.clear();
    }

    /** AOE：对目标周围按 aoe-damage-ratio 造成溅射。 */
    public List<LivingEntity> aoeFrom(LivingEntity center, double radius) {
        if (center == null || !DamageUtils.isAlive(center)) return new java.util.ArrayList<>();
        double r = radius > 0 ? radius : stats.aoeRadius();
        List<LivingEntity> list = TargetFilter.filter(player,
                com.skillcore.utils.AreaUtils.getSphere(center.getLocation(), r,
                        e -> TargetFilter.isAttackable(player, e)));
        list.remove(center);
        for (LivingEntity e : list) {
            damageCustom(e, stats.damage() * stats.aoeDamageRatio());
        }
        return list;
    }

    /** AOE：以瞄准点为中心。 */
    public List<LivingEntity> aoeAtAim(double radius) {
        double r = radius > 0 ? radius : stats.aoeRadius();
        List<LivingEntity> list = TargetFilter.filter(player,
                com.skillcore.utils.AreaUtils.getSphere(aimLocation(), r,
                        e -> TargetFilter.isAttackable(player, e)));
        for (LivingEntity e : list) {
            damageCustom(e, stats.damage() * stats.aoeDamageRatio());
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 吸血 / 回复
    // ------------------------------------------------------------------

    /** 按武器 lifesteal 回血。 */
    public double healSelfByDamage(double damageDealt) {
        return LifestealUtils.healByDamagePercent(player, damageDealt, stats.lifesteal());
    }

    /** 按数值里的 heal-amount / heal-percent-of-max 回血。 */
    public double healSelf() {
        double amount = stats.healAmount()
                + com.skillcore.utils.DamageUtils.getMaxHealth(player) * stats.healPercentOfMax();
        return LifestealUtils.heal(player, amount);
    }

    // ------------------------------------------------------------------
    // 位移（吃武器数值）
    // ------------------------------------------------------------------

    /** 向前突刺：速度= dash-speed，距离可作视觉。 */
    public void dashForward() {
        DisplacementUtils.dash(player, stats.dashSpeed(), stats.dashY());
        DisplacementUtils.playDashEffect(player.getLocation());
    }

    /** 向指定方向突刺。 */
    public void dashTowards(Location targetLoc) {
        DisplacementUtils.dashTo(player, targetLoc, stats.dashSpeed());
        DisplacementUtils.playDashEffect(player.getLocation());
    }

    /** 向前闪现 blink-distance。 */
    public void blinkForward() {
        DisplacementUtils.blinkForward(player, stats.blinkDistance());
    }

    /** 击退目标。 */
    public void knockback(LivingEntity victim) {
        DisplacementUtils.knockbackFrom(victim, player, stats.knockbackStrength(), 0.3);
    }

    /** 拉拽目标。 */
    public void pullTarget() {
        if (hasTarget()) {
            DisplacementUtils.pullToEntity(target, player, stats.pullStrength());
        }
    }

    /** 后撤步。 */
    public void backstep(double speed) {
        var dir = player.getLocation().getDirection().multiply(-1);
        DisplacementUtils.dash(player, dir, speed, 0.1);
    }

    // ------------------------------------------------------------------
    // 控制
    // ------------------------------------------------------------------

    public void slowTarget() {
        if (hasTarget()) PotionUtils.slowness(target, stats.slowDurationTicks(), stats.slowAmplifier());
    }

    public void stunTarget() {
        if (hasTarget()) PotionUtils.stun(target, stats.stunDurationTicks());
    }

    public void rootTarget() {
        if (hasTarget()) PotionUtils.root(target, stats.rootDurationTicks());
    }

    // ------------------------------------------------------------------
    // 特效
    // ------------------------------------------------------------------

    /**
     * 播放一个特效（以施法者眼睛位置为起点，带朝向/施法者/目标）。
     * <pre>
     * ctx.play(Effects.ring(Particle.END_ROD, 2.0, 24));
     * </pre>
     */
    public void play(SkillEffect effect) {
        if (effect == null) return;
        effect.play(effectContext());
    }

    /** 在指定位置播放特效。 */
    public void playAt(SkillEffect effect, Location location) {
        if (effect == null) return;
        effect.play(EffectContext.of(location).withCaster(player).withTarget(target));
    }

    /** 当前特效上下文（位置=眼睛，朝向=视线）。 */
    public EffectContext effectContext() {
        return EffectContext.of(player.getEyeLocation())
                .withDirection(player.getLocation().getDirection())
                .withCaster(player)
                .withTarget(hasTarget() ? target : null);
    }

    public void hitFx(LivingEntity victim) {
        ParticleUtils.hitMarker(victim, parseParticle(stats.particle()));
        SoundUtils.play(player.getLocation(), parseSound(stats.sound()), stats.soundVolume(), stats.soundPitch());
    }

    public void castFx() {
        ParticleUtils.castCircle(player, parseParticle(stats.particle()), 1.2);
        SoundUtils.castSuccess(player);
    }

    public void lineToTarget() {
        if (hasTarget()) {
            ParticleUtils.line(eyeLocation(), target.getEyeLocation(), parseParticle(stats.particle()), 0.35);
        }
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            return Particle.CRIT;
        }
    }

    private org.bukkit.Sound parseSound(String name) {
        try {
            return org.bukkit.Sound.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            return org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG;
        }
    }
}
