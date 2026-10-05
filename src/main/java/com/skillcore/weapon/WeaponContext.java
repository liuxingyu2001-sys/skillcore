package com.skillcore.weapon;

import com.skillcore.utils.AimUtils;
import com.skillcore.utils.DamageUtils;
import com.skillcore.utils.DisplacementUtils;
import com.skillcore.utils.EntityUtils;
import com.skillcore.utils.LifestealUtils;
import com.skillcore.utils.ParticleUtils;
import com.skillcore.utils.PercentageDamageUtils;
import com.skillcore.utils.PotionUtils;
import com.skillcore.utils.SoundUtils;
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
        if (victim == null || !DamageUtils.isAlive(victim)) return 0;
        double dealt = stats.calculateTotal(victim, power);
        if (dealt > 0) {
            DamageUtils.damage(victim, dealt, player);
            hitFx(victim);
        }
        return dealt;
    }

    /** 仅物理伤害（不吃百分比）。 */
    public double damagePhysical(LivingEntity victim) {
        if (victim == null || !DamageUtils.isAlive(victim)) return 0;
        double dealt = stats.calculateHit(victim, power);
        if (dealt > 0) DamageUtils.damage(victim, dealt, player);
        return dealt;
    }

    /** 仅百分比伤害。 */
    public double damagePercent(LivingEntity victim) {
        if (victim == null || !DamageUtils.isAlive(victim)) return 0;
        double dealt = stats.calculatePercentBonus(victim);
        if (dealt > 0) DamageUtils.damage(victim, dealt, player);
        return dealt;
    }

    /** 自定义基础伤害（覆盖 stats.damage）。 */
    public double damageCustom(LivingEntity victim, double baseDamage) {
        if (victim == null || !DamageUtils.isAlive(victim)) return 0;
        double dealt = DamageUtils.calculateFinalDamage(
                baseDamage, stats.damageScaling(), power,
                stats.criticalChance(), stats.criticalMultiplier(),
                stats.armorPenetration(), victim);
        if (dealt > 0) DamageUtils.damage(victim, dealt, player);
        return dealt;
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
        var scheduler = plugin.getServer().getScheduler();
        for (int i = 1; i < count; i++) {
            final double ratio = 1.0;
            scheduler.runTaskLater(plugin, () -> {
                if (DamageUtils.isAlive(victim) && DamageUtils.isAlive(player)) {
                    damage(victim);
                }
            }, (long) (stats.hitIntervalTicks() * i));
        }
        return first;
    }

    /** AOE：对目标周围按 aoe-damage-ratio 造成溅射。 */
    public List<LivingEntity> aoeFrom(LivingEntity center, double radius) {
        double r = radius > 0 ? radius : stats.aoeRadius();
        List<LivingEntity> list = com.skillcore.utils.AreaUtils.exclude(
                com.skillcore.utils.AreaUtils.getSphere(center.getLocation(), r, DamageUtils::isAlive),
                player, center);
        for (LivingEntity e : list) {
            damageCustom(e, stats.damage() * stats.aoeDamageRatio());
        }
        return list;
    }

    /** AOE：以瞄准点为中心。 */
    public List<LivingEntity> aoeAtAim(double radius) {
        double r = radius > 0 ? radius : stats.aoeRadius();
        List<LivingEntity> list = com.skillcore.utils.AreaUtils.exclude(
                com.skillcore.utils.AreaUtils.getSphere(aimLocation(), r, DamageUtils::isAlive),
                player);
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
            return Particle.valueOf(name.toUpperCase());
        } catch (Exception ex) {
            return Particle.CRIT;
        }
    }

    private org.bukkit.Sound parseSound(String name) {
        try {
            return org.bukkit.Sound.valueOf(name.toUpperCase());
        } catch (Exception ex) {
            return org.bukkit.Sound.ENTITY_PLAYER_ATTACK_STRONG;
        }
    }
}
